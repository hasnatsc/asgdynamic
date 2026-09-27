package com.asg.fabricerp.supply;

import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.inventory.item.InventoryItem;
import com.asg.fabricerp.inventory.item.UnitOfMeasure;
import com.asg.fabricerp.production.FabricStockService;
import com.asg.fabricerp.security.AuthorityChecks;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.asg.fabricerp.common.AuditableEntity.idOf;

/**
 * The JSON the purchase and store screens read: a grid row, and a document's full detail - header,
 * lines with where each stands downstream, what each was posted at, what may be done to it now,
 * and what was raised against it.
 */
@Component
public class SupplyViews {

    private static final Set<BusinessDocumentStatus> OPEN = EnumSet.of(BusinessDocumentStatus.APPROVED,
        BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL);

    /** How a child document's committed quantity reads on its parent's line. */
    private static final Map<DocumentType, String> TAKEN_LABEL = Map.of(
        DocumentType.PURCHASE_REQUISITION, "On purchase req.",
        DocumentType.MATERIAL_ISSUE, "Issued",
        DocumentType.PURCHASE_ORDER, "Ordered",
        DocumentType.GOODS_RECEIPT_NOTE, "Received",
        DocumentType.PURCHASE_RETURN, "Returned",
        DocumentType.TRANSFER_ISSUE, "Issued",
        DocumentType.TRANSFER_RECEIVE, "Received",
        DocumentType.FABRIC_TRANSFER_RECEIVE, "Received",
        DocumentType.IMPORT_PROFORMA_INVOICE, "On import PI");

    private final SupplyDocumentService documents;
    private final SupplyQueries queries;
    private final FabricStockService fabric;

    public SupplyViews(SupplyDocumentService documents, SupplyQueries queries, FabricStockService fabric) {
        this.documents = documents;
        this.queries = queries;
        this.fabric = fabric;
    }

    // ----------------------------------------------------------------------------------- rows

    /** The header as the grid and the viewer show it; lazy associations must already be readable. */
    public static Map<String, Object> header(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("documentType", d.getDocumentType().name());
        row.put("documentDate", d.getDocumentDate());
        row.put("requiredDate", d.getRequiredDate());
        BusinessDocument parent = d.getParentDocument();
        row.put("parentId", idOf(parent));
        row.put("parentNo", parent != null && Hibernate.isInitialized(parent) ? parent.getDocumentNo() : null);
        row.put("partyId", idOf(d.getParty()));
        row.put("partyName", d.getParty() != null && Hibernate.isInitialized(d.getParty()) ? d.getParty().getName() : null);
        row.put("warehouseId", idOf(d.getWarehouse()));
        row.put("warehouseName", d.getWarehouse() != null && Hibernate.isInitialized(d.getWarehouse()) ? d.getWarehouse().getName() : null);
        row.put("toWarehouseId", idOf(d.getToWarehouse()));
        row.put("toWarehouseName", d.getToWarehouse() != null && Hibernate.isInitialized(d.getToWarehouse()) ? d.getToWarehouse().getName() : null);
        row.put("purchaseType", d.getPurchaseType());
        row.put("purchaseTypeLabel", d.getPurchaseType() == null ? null : d.getPurchaseType().label());
        row.put("requisitionType", d.getRequisitionType());
        row.put("requisitionTypeLabel", d.getRequisitionType() == null ? null : d.getRequisitionType().label());
        row.put("department", d.getDepartment());
        row.put("currency", d.getCurrencyCode());
        row.put("exchangeRate", d.getExchangeRate());
        row.put("leadTimeDays", d.getLeadTimeDays());
        row.put("referenceNo", d.getReferenceNo());
        row.put("invoiceNo", d.getInvoiceNo());
        row.put("vehicleNo", d.getVehicleNo());
        row.put("remarks", d.getRemarks());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("subtotalAmount", d.getSubtotalAmount());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    /** A grid row with its lazy labels read - for use inside a transaction. */
    @Transactional(readOnly = true)
    public Map<String, Object> gridRow(BusinessDocument d) {
        if (d.getParentDocument() != null) Hibernate.initialize(d.getParentDocument());
        if (d.getParty() != null) Hibernate.initialize(d.getParty());
        if (d.getWarehouse() != null) Hibernate.initialize(d.getWarehouse());
        if (d.getToWarehouse() != null) Hibernate.initialize(d.getToWarehouse());
        return header(d);
    }

    /** One line as the editor and the viewer show it. */
    public static Map<String, Object> lineOf(BusinessDocumentColorLine l) {
        BusinessDocumentLineGroup g = l.getLineGroup();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("lineNo", g.getGroupNo());
        m.put("documentId", g.getDocument().getId());
        m.put("documentNo", g.getDocument().getDocumentNo());
        InventoryItem item = g.getItem();
        m.put("itemId", item == null ? null : item.getId());
        m.put("itemCode", item == null ? null : item.getItemCode());
        m.put("itemName", item == null ? null : item.getName());
        m.put("itemType", item == null ? null : item.getItemType());
        m.put("uom", uomLabel(g.getUom()));
        m.put("brandId", idOf(g.getItemBrand()));
        m.put("brandName", g.getItemBrand() == null ? null : g.getItemBrand().getName());
        m.put("modelId", idOf(g.getItemModel()));
        m.put("modelName", g.getItemModel() == null ? null : g.getItemModel().getName());
        m.put("specification", g.getItemSpecification());
        m.put("originCountry", g.getOriginCountry());
        FabricSpec f = g.getFabric();
        m.put("construction", f.getConstruction());
        m.put("composition", f.getComposition());
        m.put("weaveType", f.getWeaveType());
        m.put("finishType", f.getFinishType());
        m.put("finishWidth", f.getFinishWidth());
        m.put("gsm", f.getGsm());
        m.put("warpCount", f.getWarpCount1());
        m.put("weftCount", f.getWeftCount1());
        m.put("epi", f.getEpi());
        m.put("ppi", f.getPpi());
        m.put("fabricType", f.getFabricType());
        m.put("colorName", l.getColorName());
        m.put("colorCode", l.getColorCode());
        m.put("lotId", l.getFabricLotId());
        m.put("dyeLot", l.getDyeLot());
        m.put("shade", l.getShade());
        m.put("grade", l.getGrade());
        m.put("rolls", l.getRolls());
        m.put("quantity", l.getQuantity());
        m.put("rate", l.getRate());
        m.put("lineAmount", l.getLineAmount());
        m.put("stockDirection", l.getStockDirection());
        m.put("conditionNote", l.getConditionNote());
        m.put("customsDuty", l.getCustomsDuty());
        m.put("supplementaryDuty", l.getSupplementaryDuty());
        m.put("allocatedCost", l.getAllocatedCost());
        m.put("remarks", l.getRemarks());
        m.put("shortClosed", l.isShortClosed());
        m.put("shortClosedQuantity", l.getShortClosedQuantity());
        m.put("shortCloseReason", l.getShortCloseReason());
        return m;
    }

    // --------------------------------------------------------------------------------- detail

    /** A document's detail, loaded and read in one transaction (open-in-view is off). */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(SupplyStep step, Long id) {
        return detail(step, documents.get(step, id));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(SupplyStep step, BusinessDocument d) {
        Map<String, Object> out = gridRow(d);
        out.put("step", step.name());
        out.put("kind", step.label());
        BusinessDocument parent = d.getParentDocument();
        if (parent != null) {
            Optional<SupplyStep> parentStep = SupplyStep.of(parent.getDocumentType());
            out.put("parent", Map.of("id", parent.getId(), "documentNo", parent.getDocumentNo(),
                "slug", parentStep.map(SupplyStep::slug).orElse(""),
                "label", parentStep.map(SupplyStep::label).orElse(parent.getDocumentType().label())));
        }

        boolean maker = AuthorityChecks.holds(step.authority("CREATE")) || AuthorityChecks.holds(step.authority("AMEND"));
        boolean amender = AuthorityChecks.holds(step.authority("AMEND"));
        BusinessDocumentStatus s = d.getStatus();
        out.put("editable", maker && s.isEditable());
        out.put("deletable", s.isEditable() && AuthorityChecks.holds(step.authority("DELETE")));
        out.put("submittable", !step.isPosting() && s.isEditable() && maker);
        out.put("postable", step.isPosting() && s == BusinessDocumentStatus.DRAFT && AuthorityChecks.holds(step.authority("CREATE")));
        out.put("cancellable", amender && s != BusinessDocumentStatus.SUBMITTED && s != BusinessDocumentStatus.CANCELLED
            && s != BusinessDocumentStatus.CLOSED && s != BusinessDocumentStatus.COMPLETED);
        out.put("closable", amender && s == BusinessDocumentStatus.COMPLETED);
        out.put("shortClosable", amender && !step.isPosting() && step != SupplyStep.SA && OPEN.contains(s)
            && step.type().fulfilledBy() != null);

        List<BusinessDocumentColorLine> lines = SupplyDraws.lines(d);
        List<Long> lineIds = lines.stream().map(BusinessDocumentColorLine::getId).toList();
        Map<DocumentType, Map<Long, BigDecimal>> downstream = new LinkedHashMap<>();
        for (SupplyStep child : SupplyStep.childrenOf(step.type())) {
            downstream.put(child.type(), queries.committed(child.type(), lineIds));
        }
        if (step == SupplyStep.SPR) downstream.put(DocumentType.IMPORT_PROFORMA_INVOICE,
            queries.committed(DocumentType.IMPORT_PROFORMA_INVOICE, lineIds));
        DocumentType principal = step.type().fulfilledBy();
        boolean moves = step.isPosting() || step == SupplyStep.SA;
        Map<Long, BigDecimal[]> posted = moves && step.lines() == SupplyStep.Lines.ITEM ? queries.postedByLine(d.getId()) : Map.of();
        Map<Long, String> lots = new HashMap<>();
        for (BusinessDocumentColorLine l : lines) {
            if (l.getFabricLotId() == null) continue;
            FabricStockService.Lot lot = fabric.lot(d.getOrganizationId(), l.getFabricLotId());
            if (lot != null) lots.put(l.getId(), lot.label());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal postedValue = BigDecimal.ZERO;
        for (BusinessDocumentColorLine l : lines) {
            Map<String, Object> line = lineOf(l);
            BusinessDocumentColorLine source = l.getSourceColorLine();
            line.put("sourceId", source == null ? null : source.getId());
            line.put("sourceLabel", source == null ? null : "%s line %d".formatted(source.getLineGroup().getDocument().getDocumentNo(),
                source.getLineGroup().getGroupNo()));
            line.put("sourceDocumentId", source == null ? null : source.getLineGroup().getDocument().getId());
            line.put("lotLabel", lots.get(l.getId()));

            List<Map<String, Object>> figures = new ArrayList<>();
            downstream.forEach((type, byLine) -> figures.add(figure(TAKEN_LABEL.getOrDefault(type, type.label()),
                byLine.getOrDefault(l.getId(), BigDecimal.ZERO))));
            if (principal != null && s.isCommitted()) {
                BigDecimal done = downstream.getOrDefault(principal, Map.of()).getOrDefault(l.getId(), BigDecimal.ZERO);
                if (step == SupplyStep.SPR) {
                    done = done.add(downstream.getOrDefault(DocumentType.IMPORT_PROFORMA_INVOICE, Map.of()).getOrDefault(l.getId(), BigDecimal.ZERO));
                }
                BigDecimal open = l.isShortClosed() ? BigDecimal.ZERO
                    : l.getQuantity().subtract(done).max(BigDecimal.ZERO);
                figures.add(figure(step == SupplyStep.TI || step == SupplyStep.FTI ? "In transit" : "Open", open));
            }
            line.put("figures", figures);
            BigDecimal[] p = posted.get(l.getId());
            if (p != null) {
                line.put("postedUnitCost", p[2]);
                line.put("postedValue", p[1].abs());
                postedValue = postedValue.add(p[1].abs());
            }
            rows.add(line);
        }
        out.put("lines", rows);
        if (!posted.isEmpty()) out.put("postedValue", postedValue);
        out.put("children", queries.childrenOf(d.getId()).stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>(c);
            Optional<SupplyStep> cs = SupplyStep.of(DocumentType.valueOf((String) c.get("documentType")));
            m.put("slug", cs.map(SupplyStep::slug).orElse(null));
            m.put("label", cs.map(SupplyStep::label).orElse((String) c.get("documentType")));
            return m;
        }).toList());
        return out;
    }

    private static Map<String, Object> figure(String label, BigDecimal value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        return m;
    }

    /** A unit as people read it: its symbol (kg, yd), else its code. */
    static String uomLabel(UnitOfMeasure u) {
        if (u == null) return null;
        return u.getSymbol() != null && !u.getSymbol().isBlank() ? u.getSymbol() : u.getCode();
    }
}
