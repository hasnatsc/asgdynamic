package com.asg.fabricerp.supply;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.inventory.item.*;
import com.asg.fabricerp.party.PartyRoleType;
import com.asg.fabricerp.party.PartyService;
import com.asg.fabricerp.production.FabricStockService;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asg.fabricerp.supply.SupplyDraws.lineName;

/**
 * Raising, editing and deleting the purchase and store documents ({@link SupplyStep}).
 *
 * <p>A line raised against a parent line takes its item, unit, brand, model, specification and -
 * for an MRR or a return - its price from that line, and draws the parent line's own stream:
 * refused, with the exact over-quantity, when it would pass what the parent line holds. A direct
 * line names an active item (or, on a fabric transfer, a fabric lot) itself. Posting, cancelling
 * and short-closing are in {@link SupplyPostingService}; approval stays with the approval engine.
 */
@Service
public class SupplyDocumentService {

    private final BusinessDocumentRepository repository;
    private final BusinessNumberService numbering;
    private final DocumentReferences references;
    private final PartyService parties;
    private final WarehouseRepository warehouses;
    private final InventoryItemRepository items;
    private final ItemBrandRepository brands;
    private final ItemModelRepository models;
    private final FabricStockService fabric;
    private final SupplyDraws draws;
    private final SupplyQueries queries;
    private final OrgContext context;
    private final EntityManager em;

    public SupplyDocumentService(BusinessDocumentRepository repository, BusinessNumberService numbering,
                                 DocumentReferences references, PartyService parties, WarehouseRepository warehouses,
                                 InventoryItemRepository items, ItemBrandRepository brands, ItemModelRepository models,
                                 FabricStockService fabric, SupplyDraws draws, SupplyQueries queries, OrgContext context,
                                 EntityManager em) {
        this.repository = repository;
        this.numbering = numbering;
        this.references = references;
        this.parties = parties;
        this.warehouses = warehouses;
        this.items = items;
        this.brands = brands;
        this.models = models;
        this.fabric = fabric;
        this.draws = draws;
        this.queries = queries;
        this.context = context;
        this.em = em;
    }

    // ------------------------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Page<BusinessDocument> search(SupplyStep step, BusinessDocumentStatus status, LocalDate from, LocalDate to,
                                         String query, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), context.requireBusinessUnitId(),
            step.type(), status, from, to, query, context.requireRowScope(), pageable);
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(SupplyStep step, Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == step.type())
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException(step.label() + " not found: " + id));
    }

    // ------------------------------------------------------------------------------------ save

    @Transactional
    public BusinessDocument save(SupplyStep step, SupplyDocumentRequest request) {
        if (request.lines().isEmpty()) {
            throw new IllegalArgumentException("Add at least one line to the " + step.label().toLowerCase());
        }
        BusinessDocument doc;
        if (request.id() == null) {
            doc = new BusinessDocument();
            doc.setDocumentType(step.type());
            doc.setOrganizationId(context.requireOrganizationId());
            doc.setBusinessUnit(references.currentBusinessUnit());
        } else {
            doc = get(step, request.id());
            doc.assertEditable();
            if (draws.holdsDraws(doc)) draws.releaseAll(step, doc);
        }

        List<Source> sources = resolve(step, request.lines());
        BusinessDocument parent = sources.stream().map(Source::parentDocument).filter(Objects::nonNull).findFirst().orElse(null);
        checkOneParent(step, parent, sources);

        applyHeader(step, request, doc, parent);
        doc.setLineGroups(buildGroups(step, doc, sources));
        validate(step, doc);
        doc.recalculateTotals();

        if (doc.getDocumentNo() == null) {
            doc.setDocumentNo(numbering.next(step.type(), doc.getDocumentDate(), doc.getBusinessUnit()));
        }
        BusinessDocument saved = repository.saveAndFlush(doc);
        draws.drawAll(step, saved);
        return saved;
    }

    /** A request line with what it names, loaded and checked. */
    record Source(SupplyDocumentRequest.Line request, BusinessDocumentColorLine parentLine, InventoryItem item,
                  FabricStockService.Lot lot) {
        BusinessDocument parentDocument() {
            return parentLine == null ? null : parentLine.getLineGroup().getDocument();
        }
    }

    private List<Source> resolve(SupplyStep step, List<SupplyDocumentRequest.Line> lines) {
        Long orgId = context.requireOrganizationId();
        List<Source> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SupplyDocumentRequest.Line l : lines) {
            Source source;
            if (l.sourceId() != null) {
                if (!step.hasParent()) {
                    throw new IllegalArgumentException("A %s is not raised against another document".formatted(step.label().toLowerCase()));
                }
                BusinessDocumentColorLine line = em.find(BusinessDocumentColorLine.class, l.sourceId());
                BusinessDocument parent = line == null ? null : line.getLineGroup().getDocument();
                if (parent == null || !orgId.equals(parent.getOrganizationId()) || Boolean.TRUE.equals(parent.getDeleted())
                    || !parent.isVisibleTo(context.requireRowScope())) {
                    throw new IllegalArgumentException("Parent line not found: " + l.sourceId());
                }
                if (!step.parentTypes().contains(parent.getDocumentType())) {
                    throw new IllegalArgumentException("%s is not a %s".formatted(parent.getDocumentNo(), step.parentType().label()));
                }
                if (!step.acceptsParentStatus(parent.getStatus())) {
                    throw new IllegalStateException("%s is %s: a %s is raised only against an approved, open %s"
                        .formatted(parent.getDocumentNo(), parent.getStatus().label().toLowerCase(), step.label().toLowerCase(),
                            step.parentType().label()));
                }
                if (line.isShortClosed()) {
                    throw new IllegalStateException("%s on %s is short-closed (%s); nothing more can be raised against it"
                        .formatted(lineName(line), parent.getDocumentNo(), line.getShortCloseReason()));
                }
                if (!seen.add("SOURCE:" + l.sourceId())) {
                    throw new IllegalArgumentException("%s appears twice; put its whole quantity on one line".formatted(lineName(line)));
                }
                FabricStockService.Lot lot = step.lines() == SupplyStep.Lines.FABRIC_LOT && line.getFabricLotId() != null
                    ? fabric.lot(orgId, line.getFabricLotId()) : null;
                source = new Source(l, line, line.getLineGroup().getItem(), lot);
            } else {
                if (!step.allowsDirect()) {
                    throw new IllegalArgumentException("Every line of a %s is raised against a %s line; choose the %s first"
                        .formatted(step.label().toLowerCase(), step.parentType().label(), step.parentType().label()));
                }
                if (step.lines() == SupplyStep.Lines.FABRIC_LOT) {
                    if (l.lotId() == null) throw new IllegalArgumentException("Choose the fabric lot for every line");
                    FabricStockService.Lot lot = fabric.lot(orgId, l.lotId());
                    if (lot == null) throw new IllegalArgumentException("Fabric lot not found: " + l.lotId());
                    if (!seen.add("LOT:" + l.lotId())) {
                        throw new IllegalArgumentException("A lot appears twice; put its whole quantity on one line");
                    }
                    source = new Source(l, null, null, lot);
                } else {
                    if (l.itemId() == null) throw new IllegalArgumentException("Choose the item for every line");
                    InventoryItem item = items.findScoped(l.itemId(), orgId)
                        .orElseThrow(() -> new IllegalArgumentException("Item not found: " + l.itemId()));
                    if (!Boolean.TRUE.equals(item.getActive())) {
                        throw new IllegalArgumentException(item.getName() + " is inactive and cannot be used on a new line");
                    }
                    boolean buying = step == SupplyStep.SPR || step == SupplyStep.PO;
                    if (!buying && !item.getItemType().isStockItem()) {
                        throw new IllegalArgumentException("%s is a service; it is bought, not held in a store".formatted(item.getName()));
                    }
                    String key = "ITEM:%d:%s:%s:%s".formatted(item.getId(), l.brandId(), l.modelId(), l.stockDirection());
                    if (!seen.add(key)) {
                        throw new IllegalArgumentException("%s appears twice; put its whole quantity on one line".formatted(item.getName()));
                    }
                    source = new Source(l, null, item, null);
                }
            }
            if (l.quantity() == null || l.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Give a quantity for %s".formatted(sourceName(source)));
            }
            out.add(source);
        }
        return out;
    }

    /** All of a document's lines come from one parent document, or none do. */
    private void checkOneParent(SupplyStep step, BusinessDocument parent, List<Source> sources) {
        if (parent == null) return;
        for (Source s : sources) {
            if (s.parentDocument() == null) {
                throw new IllegalArgumentException("A %s raised against %s takes all its lines from it; raise the other items on a separate %s"
                    .formatted(step.label().toLowerCase(), parent.getDocumentNo(), step.label().toLowerCase()));
            }
            if (!s.parentDocument().getId().equals(parent.getId())) {
                throw new IllegalArgumentException("All lines must come from %s; %s is another %s"
                    .formatted(parent.getDocumentNo(), s.parentDocument().getDocumentNo(), step.parentType().label()));
            }
        }
    }

    private void applyHeader(SupplyStep step, SupplyDocumentRequest r, BusinessDocument doc, BusinessDocument parent) {
        doc.setParentDocument(parent);
        doc.setDocumentDate(r.documentDate() != null ? r.documentDate()
            : doc.getDocumentDate() != null ? doc.getDocumentDate() : LocalDate.now());
        doc.setRequiredDate(r.requiredDate() != null ? r.requiredDate() : parent != null && doc.getId() == null ? parent.getRequiredDate() : doc.getRequiredDate());
        doc.setRemarks(blank(r.remarks()));
        doc.setReferenceNo(blank(r.referenceNo()));
        if (r.leadTimeDays() != null && r.leadTimeDays() < 0) throw new IllegalArgumentException("Lead time cannot be negative");
        doc.setLeadTimeDays(step == SupplyStep.SR || step == SupplyStep.SPR || step == SupplyStep.PO || step == SupplyStep.ST
            ? r.leadTimeDays() : null);

        switch (step) {
            case SR -> {
                doc.setRequisitionType(r.requisitionType() != null ? r.requisitionType() : RequisitionType.DEPARTMENTAL);
                doc.setDepartment(blank(r.department()));
            }
            case PO -> {
                if (parent != null && parent.getDocumentType() == DocumentType.IMPORT_PROFORMA_INVOICE) {
                    // Ordered from the supplier's PI: its supplier, currency and rate, and an import.
                    doc.setParty(parent.getParty());
                    doc.setPurchaseType(PurchaseType.IMPORT);
                    doc.setCurrencyCode(parent.getCurrencyCode());
                    doc.setExchangeRate(parent.getExchangeRate());
                    break;
                }
                doc.setParty(r.supplierId() == null ? null : parties.requireHolder(r.supplierId(), PartyRoleType.SUPPLIER));
                doc.setPurchaseType(r.purchaseType() != null ? r.purchaseType() : PurchaseType.DIRECT);
                String currency = r.currencyCode() == null || r.currencyCode().isBlank() ? "BDT" : r.currencyCode().strip().toUpperCase(Locale.ROOT);
                if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency is a three-letter code, e.g. BDT or USD");
                doc.setCurrencyCode(currency);
                BigDecimal rate = "BDT".equals(currency) ? BigDecimal.ONE
                    : r.exchangeRate() == null ? BigDecimal.ZERO : r.exchangeRate();
                if (rate.signum() <= 0) throw new IllegalArgumentException("Give the exchange rate to taka for " + currency);
                doc.setExchangeRate(rate);
            }
            case MRR, PRT -> {
                // What was bought decides who from, in what currency, and at what rate - never retyped.
                doc.setParty(parent.getParty());
                doc.setPurchaseType(parent.getPurchaseType());
                doc.setCurrencyCode(parent.getCurrencyCode());
                doc.setExchangeRate(parent.getExchangeRate());
                doc.setVehicleNo(blank(r.vehicleNo()));
                if (step == SupplyStep.MRR) doc.setInvoiceNo(blank(r.invoiceNo()));
            }
            default -> { }
        }

        // The stores.
        Warehouse own = ownStore();
        switch (step) {
            case PRT -> doc.setWarehouse(parent.getWarehouse());
            case TRC, FTR -> {
                doc.setWarehouse(parent.getWarehouse());
                doc.setToWarehouse(parent.getToWarehouse());
            }
            case TI -> {
                doc.setWarehouse(parent != null ? parent.getWarehouse() : store(r.warehouseId(), own));
                doc.setToWarehouse(parent != null ? parent.getToWarehouse() : store(r.toWarehouseId(), null));
            }
            case ST, FTI -> {
                doc.setWarehouse(store(r.warehouseId(), own));
                doc.setToWarehouse(store(r.toWarehouseId(), null));
            }
            default -> {
                Warehouse fromParent = parent == null ? null : parent.getWarehouse();
                doc.setWarehouse(store(r.warehouseId(), fromParent != null ? fromParent : step.needsStore() ? own : null));
            }
        }
        // The vehicle that carries goods between stores.
        if (step == SupplyStep.TI || step == SupplyStep.FTI) doc.setVehicleNo(blank(r.vehicleNo()));
    }

    private List<BusinessDocumentLineGroup> buildGroups(SupplyStep step, BusinessDocument doc, List<Source> sources) {
        Long orgId = context.requireOrganizationId();
        List<BusinessDocumentLineGroup> groups = new ArrayList<>();
        for (Source s : sources) {
            SupplyDocumentRequest.Line r = s.request();
            BusinessDocumentLineGroup g = new BusinessDocumentLineGroup();
            g.setOrganizationId(orgId);
            BusinessDocumentColorLine line = new BusinessDocumentColorLine();
            BusinessDocumentColorLine parentLine = s.parentLine();

            if (parentLine != null) {
                BusinessDocumentLineGroup pg = parentLine.getLineGroup();
                g.setItem(pg.getItem());
                g.setUom(pg.getUom());
                g.setItemBrand(pg.getItemBrand());
                g.setItemModel(pg.getItemModel());
                g.setItemSpecification(pg.getItemSpecification());
                g.setOriginCountry(pg.getOriginCountry());
                g.setFabric(DocumentRevisionService.copySpec(pg.getFabric()));
                line.setSourceColorLine(parentLine);
                line.setColorName(parentLine.getColorName());
                line.setColorCode(parentLine.getColorCode());
                line.setFabricLotId(parentLine.getFabricLotId());
                // What was bought is received and returned at the price it was bought at; an order
                // placed from a supplier's PI is at the PI's price.
                if (step == SupplyStep.MRR || step == SupplyStep.PRT || importOrder(step, parentLine)) line.setRate(parentLine.getRate());
            } else if (s.lot() != null) {
                BusinessDocumentLineGroup lotGroup = em.find(BusinessDocumentLineGroup.class, s.lot().groupId());
                g.setUom(lotGroup.getUom());
                g.setItem(lotGroup.getItem());
                g.setFabric(DocumentRevisionService.copySpec(lotGroup.getFabric()));
                line.setFabricLotId(s.lot().id());
                if (s.lot().colourLineId() != null) {
                    BusinessDocumentColorLine colour = em.find(BusinessDocumentColorLine.class, s.lot().colourLineId());
                    line.setColorName(colour.getColorName());
                    line.setColorCode(colour.getColorCode());
                } else {
                    line.setColorName("Greige - all colours");
                }
                line.setDyeLot(s.lot().dyeLot());
                line.setShade(s.lot().shade());
                line.setGrade(s.lot().grade());
            } else {
                InventoryItem item = s.item();
                g.setItem(item);
                g.setUom(item.getBaseUnit());
                g.setItemBrand(item.getBrand());
                g.setItemModel(item.getModel());
            }

            // What the line itself may say.
            if (s.item() != null && step.lines() == SupplyStep.Lines.ITEM) {
                if (r.brandId() != null) g.setItemBrand(brands.findScoped(r.brandId(), orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Brand not found: " + r.brandId())));
                if (r.modelId() != null) {
                    ItemModel model = models.findScoped(r.modelId(), orgId)
                        .orElseThrow(() -> new IllegalArgumentException("Model not found: " + r.modelId()));
                    if (g.getItemBrand() != null && model.getBrand() != null && !model.getBrand().getId().equals(g.getItemBrand().getId())) {
                        throw new IllegalArgumentException("%s is a %s model, not %s".formatted(model.getName(), model.getBrand().getName(),
                            g.getItemBrand().getName()));
                    }
                    g.setItemModel(model);
                }
                if (r.specification() != null) g.setItemSpecification(blank(r.specification()));
                if (step == SupplyStep.MRR) {
                    g.setOriginCountry(blank(r.originCountry()));
                    line.setConditionNote(r.conditionNote());
                    // An import's duties at the port - part of what the goods cost to land.
                    line.setCustomsDuty(nonNegative(r.customsDuty(), sourceName(s)));
                    line.setSupplementaryDuty(nonNegative(r.supplementaryDuty(), sourceName(s)));
                }
                if (r.fabric() != null && s.item().getItemType() == ItemType.FABRICS
                    && (step == SupplyStep.SPR || step == SupplyStep.PO || step == SupplyStep.MRR || step == SupplyStep.MR)) {
                    applyFabric(g.getFabric(), r.fabric());
                }
            }
            switch (step) {
                case PO, MR -> { if (!importOrder(step, parentLine)) line.setRate(nonNegative(r.rate(), sourceName(s))); }
                case SA -> {
                    String direction = r.stockDirection() == null ? null : r.stockDirection().strip().toUpperCase(Locale.ROOT);
                    if (!"IN".equals(direction) && !"OUT".equals(direction)) {
                        throw new IllegalArgumentException("Say whether %s is added (IN) or taken away (OUT)".formatted(sourceName(s)));
                    }
                    line.setStockDirection(direction);
                    line.setRate("IN".equals(direction) ? nonNegative(r.rate(), sourceName(s)) : BigDecimal.ZERO);
                }
                default -> { }
            }
            if (step.lines() == SupplyStep.Lines.FABRIC_LOT) {
                if (r.rolls() != null && r.rolls() < 0) throw new IllegalArgumentException("Rolls cannot be negative");
                line.setRolls(r.rolls());
            }
            line.setQuantity(r.quantity());
            line.setRemarks(blank(r.remarks()));
            g.addColorLine(line);
            groups.add(g);
        }
        return groups;
    }

    /** The stores a document names must exist, take what it moves, and differ on a transfer. */
    private void validate(SupplyStep step, BusinessDocument doc) {
        if (step.needsStore() && doc.getWarehouse() == null) {
            throw new IllegalArgumentException("Choose the %s".formatted(step.isTransfer() ? "store the stock leaves" : "store"));
        }
        if (step.isTransfer()) {
            if (doc.getToWarehouse() == null) throw new IllegalArgumentException("Choose the store the stock goes to");
            if (doc.getToWarehouse().getId().equals(doc.getWarehouse().getId())) {
                throw new IllegalArgumentException("A transfer goes to another store; both are " + doc.getWarehouse().getName());
            }
        }
        if (step.lines() == SupplyStep.Lines.FABRIC_LOT) {
            Long orgId = context.requireOrganizationId();
            for (BusinessDocumentColorLine line : SupplyDraws.lines(doc)) {
                FabricStockService.Lot lot = fabric.lot(orgId, line.getFabricLotId());
                if (lot == null) throw new IllegalArgumentException("Fabric lot not found: " + line.getFabricLotId());
                Warehouse to = doc.getToWarehouse();
                boolean greige = FabricStockService.GREIGE.equals(lot.stage());
                if (greige ? !to.isHoldsGreige() : !to.isHoldsFinished()) {
                    throw new IllegalArgumentException("%s does not hold %s fabric (set its role under Fabric stock → Stores)"
                        .formatted(to.getName(), greige ? "greige" : "finished"));
                }
                if (step == SupplyStep.FTI) {
                    BigDecimal[] held = queries.fabricBalance(doc.getWarehouse().getId(), lot.id());
                    if (held[0].signum() <= 0) {
                        throw new IllegalArgumentException("%s holds none of the lot picked for %s".formatted(doc.getWarehouse().getName(), lineName(line)));
                    }
                }
            }
        }
    }

    private static void applyFabric(FabricSpec spec, SupplyDocumentRequest.FabricDetail f) {
        spec.setConstruction(blank(f.construction()));
        spec.setComposition(blank(f.composition()));
        spec.setWeaveType(blank(f.weaveType()));
        spec.setFinishType(blank(f.finishType()));
        spec.setFinishWidth(f.finishWidth());
        spec.setGsm(f.gsm());
        spec.setWarpCount1(blank(f.warpCount()));
        spec.setWeftCount1(blank(f.weftCount()));
        spec.setEpi(f.epi());
        spec.setPpi(f.ppi());
    }

    // ---------------------------------------------------------------------------------- delete

    /** A draft or rejected document; what it drew is given back. */
    @Transactional
    public void delete(SupplyStep step, Long id) {
        BusinessDocument doc = get(step, id);
        doc.assertEditable();
        if (draws.holdsDraws(doc)) draws.releaseAll(step, doc);
        doc.markDeleted();
        repository.save(doc);
    }

    // ------------------------------------------------------------------------------- lookups

    @Transactional(readOnly = true)
    public List<BusinessDocument> parents(SupplyStep step, String q, int page, int size) {
        if (!step.hasParent()) return List.of();
        return queries.openParents(step, q, page, size);
    }

    /**
     * A parent's lines this step may still draw, with what each allows: ordered, taken, open - and
     * what the store holds of the item, for steps that take stock out. {@code excludeId}: the
     * document being edited, whose own draws count as available again.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> openLines(SupplyStep step, Long parentId, Long excludeId) {
        if (!step.hasParent()) throw new IllegalArgumentException(step.plural() + " are not raised against another document");
        BusinessDocument parent = repository.findScopedWithLines(parentId, context.requireOrganizationId())
            .filter(d -> step.parentTypes().contains(d.getDocumentType()))
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException(step.parentType().label() + " not found: " + parentId));
        Map<Long, BigDecimal> own = new HashMap<>();
        if (excludeId != null) {
            BusinessDocument editing = get(step, excludeId);
            if (draws.holdsDraws(editing)) {
                for (BusinessDocumentColorLine l : SupplyDraws.lines(editing)) {
                    if (l.getSourceColorLine() != null) own.merge(l.getSourceColorLine().getId(), SupplyDraws.held(l), BigDecimal::add);
                }
            }
        }
        List<BusinessDocumentColorLine> lines = SupplyDraws.lines(parent);
        Map<Long, Map<String, BigDecimal>> streams = draws.ledger().streams(SourceKind.COLOUR,
            lines.stream().map(BusinessDocumentColorLine::getId).toList());
        // Steps that take stock out show what the store holds; a return takes it from where it was received.
        Long store = switch (step) {
            case MI, TI, PRT -> parent.getWarehouse() == null ? null : parent.getWarehouse().getId();
            default -> null;
        };
        Map<Long, BigDecimal[]> stock = queries.storeStock(store, lines.stream().map(l -> l.getLineGroup().getItem())
            .filter(Objects::nonNull).map(InventoryItem::getId).distinct().toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BusinessDocumentColorLine l : lines) {
            if (l.isShortClosed()) continue;
            BigDecimal cap = SupplyDraws.cap(l);
            BigDecimal taken = streams.getOrDefault(l.getId(), Map.of()).getOrDefault(step.stream(), BigDecimal.ZERO)
                .subtract(own.getOrDefault(l.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>(SupplyViews.lineOf(l));
            row.put("sourceId", l.getId());
            row.put("cap", cap);
            row.put("taken", taken);
            row.put("available", cap.subtract(taken).max(BigDecimal.ZERO));
            InventoryItem item = l.getLineGroup().getItem();
            if (store != null && item != null) {
                BigDecimal[] held = stock.get(item.getId());
                row.put("stock", held == null ? BigDecimal.ZERO : held[0]);
            }
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parent", SupplyViews.header(parent));
        out.put("lines", rows);
        return out;
    }

    // --------------------------------------------------------------------------------- helpers

    private Warehouse store(Long id, Warehouse fallback) {
        if (id == null) return fallback;
        return warehouses.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Store not found: " + id));
    }

    /** The signed-in user's own store, so a store keeper who works in one store never has to pick it. */
    private Warehouse ownStore() {
        Long own = context.warehouseId();
        return own == null ? null : warehouses.findScoped(own, context.requireOrganizationId()).orElse(null);
    }

    /** A purchase order line raised from an import PI line: priced by the PI. */
    private static boolean importOrder(SupplyStep step, BusinessDocumentColorLine parentLine) {
        return step == SupplyStep.PO && parentLine != null
            && parentLine.getLineGroup().getDocument().getDocumentType() == DocumentType.IMPORT_PROFORMA_INVOICE;
    }

    private static BigDecimal nonNegative(BigDecimal v, String what) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw new IllegalArgumentException("The price of %s cannot be negative".formatted(what));
        return v;
    }

    private static String sourceName(Source s) {
        if (s.parentLine() != null) return lineName(s.parentLine());
        if (s.item() != null) return s.item().getName();
        return "the lot";
    }

    static String blank(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
