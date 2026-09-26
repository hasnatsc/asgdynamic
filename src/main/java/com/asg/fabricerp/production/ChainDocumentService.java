package com.asg.fabricerp.production;

import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.party.PartyRoleType;
import com.asg.fabricerp.party.PartyService;
import com.asg.fabricerp.production.ChainSupport.Anchor;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asg.fabricerp.production.ChainSupport.qty;

/**
 * Raising, editing and deleting every document in the production chain ({@link ChainStep}).
 *
 * <p>A document is built from the parent lines it names: the server copies the fabric, colour and
 * route from each, inherits buyer, team and currency from the parent, and draws each line's
 * quantity on its parent line's own stream - refused, with the exact over-quantity, when that
 * would pass the step's cap ({@link DrawCaps}). Posting, cancelling and short-closing are in
 * {@link ChainPostingService}; approval stays with the approval engine.
 */
@Service
public class ChainDocumentService {

    private final BusinessDocumentRepository repository;
    private final BusinessNumberService numbering;
    private final DocumentReferences references;
    private final DocumentRevisionService revisions;
    private final PartyService parties;
    private final WarehouseRepository warehouses;
    private final ProcessRouteRepository routes;
    private final ChainSupport chain;
    private final ChainQueries queries;
    private final OrgContext context;
    private final EntityManager em;

    public ChainDocumentService(BusinessDocumentRepository repository, BusinessNumberService numbering,
                                DocumentReferences references, DocumentRevisionService revisions,
                                PartyService parties, WarehouseRepository warehouses, ProcessRouteRepository routes,
                                ChainSupport chain, ChainQueries queries, OrgContext context, EntityManager em) {
        this.repository = repository;
        this.numbering = numbering;
        this.references = references;
        this.revisions = revisions;
        this.parties = parties;
        this.warehouses = warehouses;
        this.routes = routes;
        this.chain = chain;
        this.queries = queries;
        this.context = context;
        this.em = em;
    }

    // ------------------------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Page<BusinessDocument> search(ChainStep step, BusinessDocumentStatus status, LocalDate from, LocalDate to,
                                         String query, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), context.requireBusinessUnitId(),
            step.type(), status, from, to, query, context.requireRowScope(), pageable);
    }

    @Transactional(readOnly = true)
    public BusinessDocument get(ChainStep step, Long id) {
        return repository.findScopedWithLines(id, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == step.type())
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException(step.label() + " not found: " + id));
    }

    // ------------------------------------------------------------------------------------ save

    @Transactional
    public BusinessDocument save(ChainStep step, ChainDocumentRequest request) {
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
            if (chain.holdsDraws(doc)) chain.releaseAll(step, doc);
        }

        List<Source> sources = resolve(step, request.lines(), doc);
        BusinessDocument parent = doc.getParentDocument() != null ? doc.getParentDocument() : sources.get(0).document();
        checkOneParent(step, parent, sources);

        applyHeader(step, request, doc, parent);
        doc.setLineGroups(buildGroups(step, request, doc, sources));
        validateStore(step, doc);
        doc.recalculateTotals();

        if (doc.getDocumentNo() == null) {
            doc.setDocumentNo(numbering.next(step.type(), doc.getDocumentDate(), doc.getBusinessUnit()));
        }
        BusinessDocument saved = repository.saveAndFlush(doc);
        if (chain.holdsDraws(saved)) chain.drawAll(step, saved);
        return saved;
    }

    /** A parent line named by an editor line, loaded and checked. */
    record Source(ChainDocumentRequest.Line request, SourceKind kind, BusinessDocumentColorLine line,
                  BusinessDocumentLineGroup group) {
        BusinessDocument document() {
            return group.getDocument();
        }
    }

    private List<Source> resolve(ChainStep step, List<ChainDocumentRequest.Line> lines, BusinessDocument doc) {
        Long orgId = context.requireOrganizationId();
        List<Source> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChainDocumentRequest.Line l : lines) {
            if (l.sourceId() == null) throw new IllegalArgumentException("Every line must name the line it is raised against");
            SourceKind kind = "GROUP".equalsIgnoreCase(l.sourceKind()) ? SourceKind.GROUP : SourceKind.COLOUR;
            BusinessDocumentColorLine line = null;
            BusinessDocumentLineGroup group;
            if (kind == SourceKind.GROUP) {
                group = em.find(BusinessDocumentLineGroup.class, l.sourceId());
            } else {
                line = em.find(BusinessDocumentColorLine.class, l.sourceId());
                group = line == null ? null : line.getLineGroup();
            }
            if (group == null || !orgId.equals(group.getOrganizationId())
                || Boolean.TRUE.equals(group.getDocument().getDeleted())
                || !group.getDocument().isVisibleTo(context.requireRowScope())) {
                throw new IllegalArgumentException("Parent line not found: " + l.sourceId());
            }
            BusinessDocument parent = group.getDocument();
            if (parent.getDocumentType() != step.parentType()) {
                throw new IllegalArgumentException("%s is not a %s".formatted(parent.getDocumentNo(), step.parentType().label()));
            }
            if (!step.acceptsParentStatus(parent.getStatus())) {
                throw new IllegalStateException("%s is %s: a %s can only be raised against an approved, open %s"
                    .formatted(parent.getDocumentNo(), parent.getStatus().label().toLowerCase(), step.label().toLowerCase(),
                        step.parentType().label()));
            }
            if (line != null && line.isShortClosed()) {
                throw new IllegalStateException("%s on %s is short-closed (%s); nothing more can be raised against it"
                    .formatted(lineName(line), parent.getDocumentNo(), line.getShortCloseReason()));
            }
            if (step.parentType() == DocumentType.PROCESSING_WORK_ORDER && parent.isBatchClosed()) {
                throw new IllegalStateException(parent.getDocumentNo() + " is batch-closed");
            }
            if (!seen.add(kind + ":" + l.sourceId())) {
                throw new IllegalArgumentException("%s appears twice; put its whole quantity on one line".formatted(
                    line != null ? lineName(line) : "Fabric line " + group.getGroupNo()));
            }
            checkSourceShape(step, kind, group, doc);
            if (l.quantity() == null || l.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Give a quantity for %s".formatted(
                    line != null ? lineName(line) : "fabric line " + group.getGroupNo()));
            }
            out.add(new Source(l, kind, line, group));
        }
        return out;
    }

    /** Which parent lines a step may draw: the route decides weaving's key and whether there is dyeing. */
    private void checkSourceShape(ChainStep step, SourceKind kind, BusinessDocumentLineGroup group, BusinessDocument doc) {
        RouteSnapshot route = group.getRoute();
        switch (step) {
            case WWO -> {
                if (!route.isSet()) throw new IllegalStateException(noRoute(group));
                SourceKind expected = route.getGreigeKey() == GreigeKey.CONSTRUCTION ? SourceKind.GROUP : SourceKind.COLOUR;
                if (kind != expected) {
                    throw new IllegalArgumentException(expected == SourceKind.GROUP
                        ? "%s is woven per fabric line (one greige for all its colours); raise the whole fabric line".formatted(group.getFabric().getFabricType())
                        : "%s is woven per colour; raise its colour lines".formatted(group.getFabric().getFabricType()));
                }
            }
            case PWO -> {
                if (!route.isSet()) throw new IllegalStateException(noRoute(group));
                if (!route.isNeedsProcessing()) {
                    throw new IllegalStateException("%s is delivered as greige; its route has no dyeing".formatted(group.getFabric().getFabricType()));
                }
                if (kind != SourceKind.COLOUR) throw new IllegalArgumentException("A dyeing work order is raised per colour line");
            }
            default -> {
                if (kind != SourceKind.COLOUR) throw new IllegalArgumentException("A " + step.label().toLowerCase() + " is raised per colour line");
            }
        }
    }

    /** One parent document per document - except a delivery schedule, which may take several orders of one buyer. */
    private void checkOneParent(ChainStep step, BusinessDocument parent, List<Source> sources) {
        for (Source s : sources) {
            BusinessDocument p = s.document();
            if (step == ChainStep.RPI) {
                if (!Objects.equals(AuditableEntity.idOf(p.getParty()), AuditableEntity.idOf(parent.getParty()))) {
                    throw new IllegalArgumentException("%s is for a different buyer; a delivery schedule is for one buyer"
                        .formatted(p.getDocumentNo()));
                }
                if (!Objects.equals(AuditableEntity.idOf(p.getMarketingTeam()), AuditableEntity.idOf(parent.getMarketingTeam()))) {
                    throw new IllegalArgumentException("%s belongs to another marketing team".formatted(p.getDocumentNo()));
                }
            } else if (!p.getId().equals(parent.getId())) {
                throw new IllegalArgumentException("All lines must come from %s; %s is another %s"
                    .formatted(parent.getDocumentNo(), p.getDocumentNo(), step.parentType().label()));
            }
        }
        if (step == ChainStep.BPO) {
            Set<String> types = new TreeSet<>();
            for (Source s : sources) types.add(fabricTypeKey(s.group()));
            if (types.size() > 1) {
                throw new IllegalArgumentException("A production order is for one fabric type; these lines are "
                    + String.join(", ", sources.stream().map(s -> String.valueOf(s.group().getFabric().getFabricType())).distinct().toList())
                    + ". Raise one per fabric type (Create from booking does this for you).");
            }
        }
    }

    private void applyHeader(ChainStep step, ChainDocumentRequest r, BusinessDocument doc, BusinessDocument parent) {
        boolean creating = doc.getId() == null;
        if (creating) {
            doc.setParentDocument(parent);
            doc.setParty(parent.getParty());
            doc.stampMarketingTeam(parent.getMarketingTeam());
            doc.setCurrencyCode(parent.getCurrencyCode());
            doc.setExchangeRate(parent.getExchangeRate());
            doc.setPriceInMeter(parent.isPriceInMeter());
            doc.setBookingType(parent.getBookingType());
            doc.setOrderType(parent.getOrderType());
            doc.setBrand(parent.getBrand());
            doc.setMarketingPerson(parent.getMarketingPerson());
            doc.setGarments(parent.getGarments());
            doc.setGarmentsAddress(parent.getGarmentsAddress());
            doc.setPreCostBuyer(parent.getPreCostBuyer());
        }
        doc.setDocumentDate(r.documentDate() != null ? r.documentDate()
            : doc.getDocumentDate() != null ? doc.getDocumentDate() : LocalDate.now());
        doc.setRequiredDate(r.requiredDate() != null ? r.requiredDate() : creating ? parent.getRequiredDate() : doc.getRequiredDate());
        doc.setRemarks(blank(r.remarks()));
        doc.setReferenceNo(blank(r.referenceNo()));

        // The store: a delivery takes its delivery order's; the rest name one.
        if (step == ChainStep.FD) {
            doc.setWarehouse(parent.getWarehouse());
        } else {
            doc.setWarehouse(r.warehouseId() == null ? null : warehouses.findScoped(r.warehouseId(), context.requireOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + r.warehouseId())));
        }
        if (step == ChainStep.WWO || step == ChainStep.PWO) {
            doc.setVendor(r.vendorId() == null ? null : parties.requireHolder(r.vendorId(), PartyRoleType.SUPPLIER));
        }
        if (step == ChainStep.PWO) {
            ProcessKind kind = r.processKind() != null ? r.processKind() : parent.getLineGroups().stream()
                .map(g -> g.getRoute().getProcessKind()).filter(Objects::nonNull).findFirst().orElse(ProcessKind.DYE);
            doc.setProcessKind(kind);
        } else if (step == ChainStep.GI || step == ChainStep.FFR) {
            doc.setProcessKind(parent.getProcessKind());
        }
        if (step == ChainStep.RPI || step == ChainStep.DO || step == ChainStep.FD) {
            if (r.garmentsId() != null) doc.setGarments(parties.requireHolder(r.garmentsId(), PartyRoleType.GARMENT_FACTORY));
            if (r.garmentsAddress() != null) doc.setGarmentsAddress(blank(r.garmentsAddress()));
        }
        if (step == ChainStep.FD) {
            doc.setVehicleNo(blank(r.vehicleNo()));
            doc.setDriverName(blank(r.driverName()));
        }
    }

    private List<BusinessDocumentLineGroup> buildGroups(ChainStep step, ChainDocumentRequest r, BusinessDocument doc,
                                                         List<Source> sources) {
        Map<Long, BigDecimal> allowances = new HashMap<>();
        for (ChainDocumentRequest.GroupSetting g : r.groups()) {
            if (g.sourceGroupId() != null && g.greigeAllowancePct() != null) allowances.put(g.sourceGroupId(), g.greigeAllowancePct());
        }
        Map<Long, BusinessDocumentLineGroup> byParentGroup = new LinkedHashMap<>();
        for (Source s : sources) {
            BusinessDocumentLineGroup parentGroup = s.group();
            BusinessDocumentLineGroup group = byParentGroup.computeIfAbsent(parentGroup.getId(), k -> {
                BusinessDocumentLineGroup g = new BusinessDocumentLineGroup();
                g.setItem(parentGroup.getItem());
                g.setUom(parentGroup.getUom());
                g.setFabric(DocumentRevisionService.copySpec(parentGroup.getFabric()));
                g.setRoute(step == ChainStep.BPO ? routeFor(parentGroup, allowances.get(parentGroup.getId()))
                                                 : parentGroup.getRoute().copy());
                return g;
            });
            group.addColorLine(buildLine(step, s));
        }
        List<BusinessDocumentLineGroup> groups = new ArrayList<>(byParentGroup.values());
        groups.forEach(g -> g.setOrganizationId(doc.getOrganizationId()));
        return groups;
    }

    /** A production order line's route, from the master - or empty, which blocks its submission. */
    private RouteSnapshot routeFor(BusinessDocumentLineGroup bookingGroup, BigDecimal allowance) {
        String fabricType = bookingGroup.getFabric().getFabricType();
        if (fabricType == null || fabricType.isBlank()) return new RouteSnapshot();
        return routes.findForFabricType(context.requireOrganizationId(), fabricType).map(route -> {
            RouteSnapshot snapshot = route.snapshot();
            if (allowance != null) snapshot.setGreigeAllowancePct(allowance);
            return snapshot;
        }).orElseGet(RouteSnapshot::new);
    }

    private BusinessDocumentColorLine buildLine(ChainStep step, Source s) {
        ChainDocumentRequest.Line r = s.request();
        BusinessDocumentColorLine line = new BusinessDocumentColorLine();
        if (s.kind() == SourceKind.GROUP) {
            line.setSourceLineGroup(s.group());
            line.setColorName("Greige - all colours");
            line.setFabricsStyle(s.group().getFabric().getConstruction());
        } else {
            BusinessDocumentColorLine src = s.line();
            line.setSourceColorLine(src);
            line.setColorCode(src.getColorCode());
            line.setColorName(src.getColorName());
            line.setFabricsStyle(src.getFabricsStyle());
            line.setColorReference(src.getColorReference());
            line.setStrikeOffReference(src.getStrikeOffReference());
            line.setLabDipReference(src.getLabDipReference());
            line.setLoomReference(src.getLoomReference());
            line.setColorSpecification(src.getColorSpecification());
            if (step == ChainStep.BPO || step == ChainStep.RPI || step == ChainStep.DO || step == ChainStep.FD) {
                line.setRate(src.getRate());
                line.setPriceInMeter(src.getPriceInMeter());
            }
            // What the parent line already fixed travels down: the lot a delivery order picked, the shade received.
            if (step == ChainStep.FD) line.setFabricLotId(src.getFabricLotId());
            if (step == ChainStep.DO || step == ChainStep.FD) line.setDeliveryDate(src.getDeliveryDate());
        }
        line.setQuantity(r.quantity());
        line.setRolls(r.rolls());
        line.setRemarks(blank(r.remarks()));
        line.setRevisedFromLineId(r.revisedFromLineId());
        switch (step) {
            case GI, DO -> line.setFabricLotId(r.lotId());
            case FFR -> {
                line.setDyeLot(r.dyeLot());
                line.setShade(r.shade());
                String grade = r.grade() == null || r.grade().isBlank() ? "A" : r.grade().strip().toUpperCase();
                if (!grade.equals("A") && !grade.equals("B")) throw new IllegalArgumentException("Grade is A or B");
                line.setGrade(grade);
            }
            case RPI -> line.setDeliveryDate(r.deliveryDate());
            default -> { }
        }
        return line;
    }

    /** A store document's store must hold what it moves; a lot it names must belong to the line. */
    /**
     * The signed-in user's own store, when it can take this document - so a store keeper who works
     * in one store never has to pick it. Null when the user has none or it holds the wrong fabric.
     */
    public Warehouse defaultStore(ChainStep step, BusinessDocument doc) {
        Long own = context.warehouseId();
        if (own == null) return null;
        Boolean greige = switch (step) {
            case GR -> true;
            case FFR -> false;
            case GI -> doc.getProcessKind() != ProcessKind.REWORK;
            case DO -> doc.getLineGroups().isEmpty() ? null
                : doc.getLineGroups().get(0).getRoute().getDeliverStage() == DeliverStage.GREIGE;
            default -> null;
        };
        if (greige == null) return null;
        return warehouses.findScoped(own, context.requireOrganizationId())
            .filter(w -> greige ? w.isHoldsGreige() : w.isHoldsFinished())
            .orElse(null);
    }

    private void validateStore(ChainStep step, BusinessDocument doc) {
        if (doc.getWarehouse() == null) doc.setWarehouse(defaultStore(step, doc));
        Warehouse store = doc.getWarehouse();
        if (store != null) {
            switch (step) {
                case GR -> requireRole(store, true, "Greige is received into a greige store");
                case FFR -> requireRole(store, false, "Finished fabric is received into a finished store");
                case GI -> requireRole(store, doc.getProcessKind() != ProcessKind.REWORK,
                    doc.getProcessKind() == ProcessKind.REWORK ? "Rework issues finished cloth from a finished store"
                                                               : "Greige is issued from a greige store");
                case DO -> {
                    Set<DeliverStage> stages = new HashSet<>();
                    for (BusinessDocumentLineGroup g : doc.getLineGroups()) stages.add(g.getRoute().getDeliverStage());
                    if (stages.size() > 1) {
                        throw new IllegalArgumentException("A delivery order sends greige or finished fabric, not both; raise one of each");
                    }
                    if (!stages.isEmpty()) {
                        boolean greige = stages.iterator().next() == DeliverStage.GREIGE;
                        requireRole(store, greige, greige ? "This order is delivered as greige, from a greige store"
                                                          : "This order is delivered finished, from a finished store");
                    }
                }
                default -> { }
            }
        }
        if (step == ChainStep.GI || step == ChainStep.DO) {
            for (BusinessDocumentLineGroup g : doc.getLineGroups()) {
                for (BusinessDocumentColorLine line : g.getColorLines()) {
                    if (line.getFabricLotId() == null) continue;
                    Anchor anchor = chain.anchor(line.getSourceColorLine());
                    if (!queries.lotFits(line.getFabricLotId(), lotRule(step, doc.getProcessKind(), anchor))) {
                        throw new IllegalArgumentException("The lot picked for %s is not stock of that order line"
                            .formatted(lineName(line)));
                    }
                }
            }
        }
    }

    private static void requireRole(Warehouse store, boolean greige, String message) {
        if (greige ? !store.isHoldsGreige() : !store.isHoldsFinished()) {
            throw new IllegalArgumentException("%s; %s is not one (set its role under Fabric stock → Stores)"
                .formatted(message, store.getName()));
        }
    }

    /** Which lots a Greige issue or delivery order line may pick. */
    public static ChainQueries.LotRule lotRule(ChainStep step, ProcessKind kind, Anchor anchor) {
        RouteSnapshot route = anchor.route();
        boolean greigeByColour = route.getGreigeKey() == GreigeKey.COLOUR;
        Long colourId = anchor.line() == null ? null : anchor.line().getId();
        if (step == ChainStep.GI && kind == ProcessKind.REWORK) {
            return new ChainQueries.LotRule(FabricStockService.FINISHED, anchor.group().getId(), colourId, "B");
        }
        if (step == ChainStep.DO && route.getDeliverStage() == DeliverStage.FINISHED) {
            return new ChainQueries.LotRule(FabricStockService.FINISHED, anchor.group().getId(), colourId, "A");
        }
        return new ChainQueries.LotRule(FabricStockService.GREIGE, anchor.group().getId(), greigeByColour ? colourId : null, null);
    }

    // ---------------------------------------------------------------------------------- delete

    /** A draft or rejected document; what it drew is given back. */
    @Transactional
    public void delete(ChainStep step, Long id) {
        BusinessDocument doc = get(step, id);
        doc.assertEditable();
        if (chain.holdsDraws(doc)) chain.releaseAll(step, doc);
        doc.markDeleted();
        repository.save(doc);
    }

    // ---------------------------------------------------------------------- production orders

    /**
     * An approved Booking becomes one draft production order per fabric type, every open colour
     * line pre-filled with what is still to order, the route copied and the greige worked out.
     */
    @Transactional
    public List<BusinessDocument> createFromBooking(Long bookingId) {
        BusinessDocument booking = repository.findScopedWithLines(bookingId, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == DocumentType.BOOKING)
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        if (!ChainStep.BPO.acceptsParentStatus(booking.getStatus())) {
            throw new IllegalStateException("%s is %s; production orders are raised from an approved booking"
                .formatted(booking.getDocumentNo(), booking.getStatus().label().toLowerCase()));
        }
        Map<Long, Map<String, BigDecimal>> drawn = chain.ledger().streams(SourceKind.COLOUR,
            booking.getLineGroups().stream().flatMap(g -> g.getColorLines().stream()).map(BusinessDocumentColorLine::getId).toList());
        Map<String, List<ChainDocumentRequest.Line>> byType = new LinkedHashMap<>();
        for (BusinessDocumentLineGroup g : booking.getLineGroups()) {
            for (BusinessDocumentColorLine l : g.getColorLines()) {
                BigDecimal open = l.getQuantity().subtract(drawn.getOrDefault(l.getId(), Map.of())
                    .getOrDefault(ChainStep.BPO.stream(), BigDecimal.ZERO));
                if (open.signum() <= 0 || l.isShortClosed()) continue;
                byType.computeIfAbsent(fabricTypeKey(g), k -> new ArrayList<>())
                    .add(new ChainDocumentRequest.Line("COLOUR", l.getId(), open, null, null, null, null, null, null, null, null));
            }
        }
        if (byType.isEmpty()) {
            throw new IllegalStateException("Everything on %s is already on production orders".formatted(booking.getDocumentNo()));
        }
        List<BusinessDocument> created = new ArrayList<>();
        for (List<ChainDocumentRequest.Line> lines : byType.values()) {
            created.add(save(ChainStep.BPO, new ChainDocumentRequest(null, LocalDate.now(), booking.getRequiredDate(),
                null, null, null, null, null, null, null, null, null, lines, List.of())));
        }
        return created;
    }

    /** A revision of an approved production order or delivery schedule; it takes over once approved. */
    @Transactional
    public BusinessDocument revise(ChainStep step, Long id, String reason) {
        if (!step.isRevisable()) throw new IllegalStateException(step.plural() + " are not revised; edit or cancel instead");
        BusinessDocument original = get(step, id);
        boolean pending = repository.revisionsOf(original.getRevisionOf() != null ? original.getRevisionOf().getId() : original.getId(),
                context.requireOrganizationId()).stream()
            .anyMatch(d -> d.getRevisionNo() > original.getRevisionNo() && !d.getStatus().isCommitted()
                && d.getStatus() != BusinessDocumentStatus.CANCELLED);
        if (pending) throw new IllegalStateException("A revision of %s is already in progress".formatted(original.getDocumentNo()));
        return revisions.revise(original, reason);
    }

    // ------------------------------------------------------------------------------- lookups

    @Transactional(readOnly = true)
    public List<BusinessDocument> parents(ChainStep step, String q, int page, int size) {
        return queries.openParents(step, q, page, size);
    }

    /**
     * The parent's lines this step may still draw, with what each allows: cap, taken, left - and
     * for store steps the lots that may be picked. {@code excludeId}: the document being edited,
     * whose own draws count as available again.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> openLines(ChainStep step, Long parentId, Long excludeId, ProcessKind kind) {
        BusinessDocument parent = repository.findScopedWithLines(parentId, context.requireOrganizationId())
            .filter(d -> d.getDocumentType() == step.parentType())
            .filter(d -> d.isVisibleTo(context.requireRowScope()))
            .orElseThrow(() -> new IllegalArgumentException(step.parentType().label() + " not found: " + parentId));
        BusinessDocument editing = excludeId == null ? null : get(step, excludeId);
        ProcessKind effectiveKind = step == ChainStep.PWO ? kind
            : (step == ChainStep.GI || step == ChainStep.FFR) ? parent.getProcessKind() : null;
        if (editing != null && step == ChainStep.PWO && kind == null) effectiveKind = editing.getProcessKind();
        String stream = DrawCaps.stream(step, effectiveKind);

        Map<String, BigDecimal> own = new HashMap<>();
        // A revision still being drafted will take over its predecessor's draws when approved, so
        // what the predecessor holds counts as open to it.
        BusinessDocument holder = editing == null ? null : chain.holdsDraws(editing) ? editing : predecessor(editing);
        if (holder != null) {
            for (BusinessDocumentLineGroup g : holder.getLineGroups()) {
                for (BusinessDocumentColorLine l : g.getColorLines()) {
                    if (chain.sourceKind(l) != null) own.merge(chain.sourceKind(l) + ":" + chain.sourceId(l), chain.held(step, l), BigDecimal::add);
                }
            }
        }
        List<Long> colourIds = parent.getLineGroups().stream().flatMap(g -> g.getColorLines().stream())
            .map(BusinessDocumentColorLine::getId).toList();
        Map<Long, Map<String, BigDecimal>> colourDraws = chain.ledger().streams(SourceKind.COLOUR, colourIds);
        Map<Long, Map<String, BigDecimal>> groupDraws = chain.ledger().streams(SourceKind.GROUP,
            parent.getLineGroups().stream().map(BusinessDocumentLineGroup::getId).toList());

        List<Map<String, Object>> out = new ArrayList<>();
        for (BusinessDocumentLineGroup g : parent.getLineGroups()) {
            RouteSnapshot route = g.getRoute();
            if (step == ChainStep.PWO && !(route.isSet() && route.isNeedsProcessing())) continue;
            boolean byGroup = step == ChainStep.WWO && route.getGreigeKey() == GreigeKey.CONSTRUCTION;
            if (byGroup) {
                BigDecimal cap = DrawCaps.cap(step, effectiveKind, g.groupQuantity(), route, null);
                BigDecimal taken = groupDraws.getOrDefault(g.getId(), Map.of()).getOrDefault(stream, BigDecimal.ZERO)
                    .subtract(own.getOrDefault("GROUP:" + g.getId(), BigDecimal.ZERO));
                Map<String, Object> row = baseRow(g, "GROUP", g.getId(), "Greige - all colours", null, g.groupQuantity(), cap, taken);
                suggest(row, route.greigeFor(g.groupQuantity()), taken);
                row.put("colours", g.getColorLines().size());
                row.put("coversColours", ChainViews.coloursOf(g));
                out.add(row);
                continue;
            }
            for (BusinessDocumentColorLine l : g.getColorLines()) {
                if (l.isShortClosed()) continue;
                BigDecimal issued = step == ChainStep.FFR
                    ? colourDraws.getOrDefault(l.getId(), Map.of()).getOrDefault(ChainStep.GI.stream(), BigDecimal.ZERO) : null;
                BigDecimal cap = DrawCaps.cap(step, effectiveKind, l.getQuantity(), route, issued);
                BigDecimal taken = colourDraws.getOrDefault(l.getId(), Map.of()).getOrDefault(stream, BigDecimal.ZERO)
                    .subtract(own.getOrDefault("COLOUR:" + l.getId(), BigDecimal.ZERO));
                Map<String, Object> row = baseRow(g, "COLOUR", l.getId(), l.getColorName(), l.getColorCode(), l.getQuantity(), cap, taken);
                row.put("rate", l.getRate());
                row.put("deliveryDate", l.getDeliveryDate());
                row.put("dyeLot", l.getDyeLot());
                row.put("shade", l.getShade());
                row.putAll(ChainViews.colourOf(l));
                if (issued != null) row.put("issued", issued);
                if (step == ChainStep.GI) row.put("plannedGreige", effectiveKind == ProcessKind.REWORK ? l.getQuantity() : route.greigeFor(l.getQuantity()));
                if (step == ChainStep.GI || step == ChainStep.DO) {
                    Anchor anchor = chain.anchor(l);
                    row.put("lots", queries.lots(lotRule(step, effectiveKind, anchor), null));
                }
                if (step == ChainStep.FD) {
                    row.put("lotId", l.getFabricLotId());
                    row.put("lots", l.getFabricLotId() == null ? List.of() : queries.lotsById(List.of(l.getFabricLotId()), parent.getWarehouse() == null ? null : parent.getWarehouse().getId()));
                    row.put("reserved", queries.reservedFor(l.getId()));
                }
                BigDecimal base = switch (step) {
                    case WWO -> route.greigeFor(l.getQuantity());
                    case GI -> effectiveKind == ProcessKind.REWORK ? l.getQuantity() : route.greigeFor(l.getQuantity());
                    case FFR -> l.getQuantity().min(issued);
                    default -> l.getQuantity();
                };
                suggest(row, base, taken);
                if (step == ChainStep.BPO) {
                    RouteSnapshot planned = routeFor(g, null);
                    row.put("routeCode", planned.getRouteCode());
                    row.put("routeLabel", planned.isSet() ? planned.getRouteCode().label() : null);
                    row.put("greigeAllowancePct", planned.isSet() ? planned.getGreigeAllowancePct() : null);
                    row.put("fabricType", g.getFabric().getFabricType());
                }
                out.add(row);
            }
        }
        return out;
    }

    /** The committed version a revision draft replaces, or null. */
    private BusinessDocument predecessor(BusinessDocument revision) {
        if (revision.getRevisionNo() == null || revision.getRevisionNo() == 0 || revision.getRevisionOf() == null) return null;
        return repository.revisionsOf(revision.getRevisionOf().getId(), context.requireOrganizationId()).stream()
            .filter(d -> d.getRevisionNo() < revision.getRevisionNo() && d.getStatus().isCommitted())
            .max(Comparator.comparing(BusinessDocument::getRevisionNo))
            .flatMap(d -> repository.findScopedWithLines(d.getId(), context.requireOrganizationId()))
            .orElse(null);
    }

    /** What the editor fills in when a line is ticked: the planned amount still to go, within what is allowed. */
    private static void suggest(Map<String, Object> row, BigDecimal base, BigDecimal taken) {
        BigDecimal available = (BigDecimal) row.get("available");
        row.put("suggested", base.subtract(taken.max(BigDecimal.ZERO)).min(available).max(BigDecimal.ZERO));
    }

    private static Map<String, Object> baseRow(BusinessDocumentLineGroup g, String kind, Long id, String colour, String code,
                                               BigDecimal quantity, BigDecimal cap, BigDecimal taken) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceKind", kind);
        row.put("sourceId", id);
        row.put("documentId", g.getDocument().getId());
        row.put("documentNo", g.getDocument().getDocumentNo());
        row.put("groupId", g.getId());
        row.put("groupNo", g.getGroupNo());
        row.putAll(ChainViews.fabricOf(g));
        row.put("uom", g.getUom() == null ? null : uomLabel(g.getUom()));
        row.put("colorName", colour);
        row.put("colorCode", code);
        row.put("quantity", quantity);
        row.put("cap", cap);
        row.put("taken", taken.max(BigDecimal.ZERO));
        row.put("available", cap.subtract(taken).max(BigDecimal.ZERO));
        RouteSnapshot route = g.getRoute();
        if (route.isSet()) {
            row.put("route", route.getRouteCode().label());
            row.put("greigeKey", route.getGreigeKey());
            row.put("deliverStage", route.getDeliverStage());
            row.put("yarnPrep", route.getYarnPrep().label());
        }
        return row;
    }

    // --------------------------------------------------------------------------------- helpers

    static String lineName(BusinessDocumentColorLine line) {
        if (line.getColorName() != null) return line.getColorName();
        if (line.getColorCode() != null) return line.getColorCode();
        return "Line " + line.getColorLineNo();
    }

    private static String noRoute(BusinessDocumentLineGroup group) {
        return "Fabric type '%s' has no process route. Add it under Master data → Process routes."
            .formatted(group.getFabric().getFabricType());
    }

    static String fabricTypeKey(BusinessDocumentLineGroup g) {
        String t = g.getFabric().getFabricType();
        return t == null ? "" : t.strip().toLowerCase(Locale.ROOT);
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    static String number(BigDecimal v) {
        return qty(v);
    }

    /** A unit as people read it: its symbol (yd, m), else its name. */
    static String uomLabel(com.asg.fabricerp.inventory.item.UnitOfMeasure u) {
        return u.getSymbol() != null && !u.getSymbol().isBlank() ? u.getSymbol() : u.getName();
    }
}
