package com.asg.fabricerp.production;

import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.production.LineDrawLedger.SourceKind;
import com.asg.fabricerp.security.AuthorityChecks;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.asg.fabricerp.common.AuditableEntity.idOf;

/**
 * The JSON the chain screens read: a grid row, and a document's full detail - header, lines with
 * where each stands downstream, what may be done to it now, and what was raised against it.
 */
@Component
public class ChainViews {

    private static final Set<BusinessDocumentStatus> OPEN = EnumSet.of(BusinessDocumentStatus.APPROVED,
        BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL);

    private final ChainSupport chain;
    private final ChainQueries queries;
    private final ChainDocumentService documents;

    public ChainViews(ChainSupport chain, ChainQueries queries, ChainDocumentService documents) {
        this.chain = chain;
        this.queries = queries;
        this.documents = documents;
    }

    /** A document's detail, loaded and read in one transaction (open-in-view is off). */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(ChainStep step, Long id) {
        return detail(step, documents.get(step, id));
    }

    /** Constructions and colours per document, for a page of the list. */
    public Map<Long, Map<String, Object>> fabricSummaries(Collection<Long> documentIds) {
        return queries.fabricSummaries(documentIds);
    }

    public static Map<String, Object> row(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("documentDate", d.getDocumentDate());
        row.put("requiredDate", d.getRequiredDate());
        BusinessDocument parent = d.getParentDocument();
        row.put("parentId", idOf(parent));
        row.put("parentNo", parent != null && Hibernate.isInitialized(parent) ? parent.getDocumentNo() : null);
        row.put("partyName", d.getParty() != null && Hibernate.isInitialized(d.getParty()) ? d.getParty().getName() : null);
        row.put("marketingTeamName", d.getMarketingTeam() != null && Hibernate.isInitialized(d.getMarketingTeam()) ? d.getMarketingTeam().getName() : null);
        row.put("warehouseName", d.getWarehouse() != null && Hibernate.isInitialized(d.getWarehouse()) ? d.getWarehouse().getName() : null);
        row.put("processKind", d.getProcessKind() == null ? null : d.getProcessKind().label());
        row.put("referenceNo", d.getReferenceNo());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("subtotalAmount", d.getSubtotalAmount());
        row.put("currency", d.getCurrencyCode());
        row.put("revisionNo", d.getRevisionNo());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    /** A grid row with its lazy labels read - for use inside a transaction. */
    @Transactional(readOnly = true)
    public Map<String, Object> gridRow(BusinessDocument d) {
        if (d.getParentDocument() != null) Hibernate.initialize(d.getParentDocument());
        if (d.getParty() != null) Hibernate.initialize(d.getParty());
        if (d.getMarketingTeam() != null) Hibernate.initialize(d.getMarketingTeam());
        if (d.getWarehouse() != null) Hibernate.initialize(d.getWarehouse());
        return row(d);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(ChainStep step, BusinessDocument d) {
        Map<String, Object> out = gridRow(d);
        out.put("step", step.name());
        out.put("kind", step.label());
        out.put("documentType", d.getDocumentType().name());
        BusinessDocument parent = d.getParentDocument();
        if (parent != null) {
            out.put("parent", Map.of("id", parent.getId(), "documentNo", parent.getDocumentNo(),
                "slug", ChainStep.of(parent.getDocumentType()).map(ChainStep::slug).orElse("booking"),
                "label", ChainStep.of(parent.getDocumentType()).map(ChainStep::label).orElse(parent.getDocumentType().label())));
        }
        out.put("warehouseId", idOf(d.getWarehouse()));
        out.put("vendorId", idOf(d.getVendor()));
        out.put("vendorName", d.getVendor() == null ? null : d.getVendor().getName());
        out.put("processKindCode", d.getProcessKind());
        out.put("garmentsId", idOf(d.getGarments()));
        out.put("garmentsName", d.getGarments() == null ? null : d.getGarments().getName());
        out.put("garmentsAddress", d.getGarmentsAddress());
        out.put("vehicleNo", d.getVehicleNo());
        out.put("driverName", d.getDriverName());
        out.put("batchClosed", d.isBatchClosed());
        out.put("remarks", d.getRemarks());
        out.put("revisionOfId", idOf(d.getRevisionOf()));

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
        out.put("shortClosable", amender && !step.isPosting() && OPEN.contains(s));
        out.put("batchClosable", amender && step == ChainStep.PWO && s.isCommitted() && s != BusinessDocumentStatus.CLOSED && !d.isBatchClosed());
        out.put("revisable", amender && step.isRevisable() && s.isCommitted() && s != BusinessDocumentStatus.CLOSED);

        List<BusinessDocumentColorLine> lines = d.getLineGroups().stream().flatMap(g -> g.getColorLines().stream()).toList();
        List<Long> lineIds = lines.stream().map(BusinessDocumentColorLine::getId).toList();
        Map<Long, Map<String, BigDecimal>> streams = chain.ledger().streams(SourceKind.COLOUR, lineIds);
        Map<Long, Map<String, BigDecimal>> groupStreams = chain.ledger().streams(SourceKind.GROUP,
            d.getLineGroups().stream().map(BusinessDocumentLineGroup::getId).toList());
        Map<Long, BigDecimal> delivered = switch (step) {
            case BPO -> queries.deliveredByBpoLine(lineIds);
            case RPI -> queries.deliveredByScheduleLine(lineIds);
            default -> Map.of();
        };
        Map<Long, Map<String, BigDecimal>> finished = step == ChainStep.PWO ? queries.finishedByGrade(lineIds) : Map.of();
        Map<Long, String> lotLabels = queries.lotLabels(lines.stream().map(BusinessDocumentColorLine::getFabricLotId)
            .filter(Objects::nonNull).distinct().toList());

        List<Map<String, Object>> groups = new ArrayList<>();
        for (BusinessDocumentLineGroup g : d.getLineGroups()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("id", g.getId());
            group.put("groupNo", g.getGroupNo());
            group.putAll(fabricOf(g));
            group.put("uom", g.getUom() == null ? null : uomLabel(g.getUom()));
            group.put("groupQuantity", g.groupQuantity());
            group.put("revisedFromGroupId", g.getRevisedFromGroupId());
            RouteSnapshot r = g.getRoute();
            if (r.isSet()) {
                Map<String, Object> route = new LinkedHashMap<>();
                route.put("code", r.getRouteCode());
                route.put("label", r.getRouteCode().label());
                route.put("greigeKey", r.getGreigeKey());
                route.put("deliverStage", r.getDeliverStage());
                route.put("needsProcessing", r.isNeedsProcessing());
                route.put("processKind", r.getProcessKind() == null ? null : r.getProcessKind().label());
                route.put("yarnPrep", r.getYarnPrep().label());
                route.put("greigeAllowancePct", r.getGreigeAllowancePct());
                route.put("receiveTolerancePct", r.getReceiveTolerancePct());
                route.put("deliveryTolerancePct", r.getDeliveryTolerancePct());
                group.put("route", route);
                if (step == ChainStep.BPO) {
                    group.put("greigeQuantity", r.greigeFor(g.groupQuantity()));
                    if (r.getGreigeKey() == GreigeKey.CONSTRUCTION) {
                        group.put("wovenOrdered", groupStreams.getOrDefault(g.getId(), Map.of())
                            .getOrDefault(ChainStep.WWO.stream(), BigDecimal.ZERO));
                    }
                }
            }
            List<Map<String, Object>> colours = new ArrayList<>();
            for (BusinessDocumentColorLine l : g.getColorLines()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("id", l.getId());
                line.put("colorLineNo", l.getColorLineNo());
                line.putAll(colourOf(l));
                line.put("quantity", l.getQuantity());
                line.put("rate", l.getRate());
                line.put("lineAmount", l.getLineAmount());
                line.put("sourceKind", chain.sourceKind(l));
                line.put("sourceId", chain.sourceId(l));
                line.put("sourceLabel", chain.sourceKind(l) == null ? null : chain.describe(l));
                line.put("sourceDocumentId", chain.sourceGroup(l) == null ? null : chain.sourceGroup(l).getDocument().getId());
                if (chain.sourceKind(l) == SourceKind.GROUP && chain.sourceGroup(l) != null) {
                    // A line woven for the whole fabric line: show which colours it covers.
                    line.put("coversColours", coloursOf(chain.sourceGroup(l)));
                }
                line.put("lotId", l.getFabricLotId());
                line.put("lotLabel", l.getFabricLotId() == null ? null : lotLabels.get(l.getFabricLotId()));
                line.put("dyeLot", l.getDyeLot());
                line.put("shade", l.getShade());
                line.put("grade", l.getGrade());
                line.put("rolls", l.getRolls());
                line.put("deliveryDate", l.getDeliveryDate());
                line.put("remarks", l.getRemarks());
                line.put("revisedFromLineId", l.getRevisedFromLineId());
                line.put("shortClosed", l.isShortClosed());
                line.put("shortClosedQuantity", l.getShortClosedQuantity());
                line.put("shortCloseReason", l.getShortCloseReason());
                line.put("figures", figures(step, d, l, streams.getOrDefault(l.getId(), Map.of()), delivered.get(l.getId()),
                    finished.getOrDefault(l.getId(), Map.of())));
                colours.add(line);
            }
            group.put("colorLines", colours);
            groups.add(group);
        }
        out.put("lineGroups", groups);
        out.put("children", queries.childrenOf(d.getId()).stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>(c);
            DocumentType t = DocumentType.valueOf((String) c.get("documentType"));
            m.put("label", ChainStep.of(t).map(ChainStep::label).orElse(t.label()));
            m.put("slug", ChainStep.of(t).map(ChainStep::slug).orElse(null));
            return m;
        }).toList());
        return out;
    }

    /** Where one line stands downstream, as label/value pairs for its row. */
    private static List<Map<String, Object>> figures(ChainStep step, BusinessDocument d, BusinessDocumentColorLine l,
                                                     Map<String, BigDecimal> s, BigDecimal delivered,
                                                     Map<String, BigDecimal> finished) {
        List<Map<String, Object>> f = new ArrayList<>();
        switch (step) {
            case BPO -> {
                RouteSnapshot r = l.getLineGroup().getRoute();
                if (r.isSet() && r.getGreigeKey() == GreigeKey.COLOUR) add(f, "Greige", r.greigeFor(l.getQuantity()));
                if (r.isSet() && r.getGreigeKey() == GreigeKey.COLOUR) add(f, "On weaving WOs", s.get(ChainStep.WWO.stream()));
                if (r.isNeedsProcessing()) add(f, "On dyeing WOs", s.get(ChainStep.PWO.stream()));
                add(f, "Scheduled", s.get(ChainStep.RPI.stream()));
                add(f, "Delivered", delivered);
            }
            case WWO -> add(f, "Received", s.get(ChainStep.GR.stream()));
            case PWO -> {
                BigDecimal issued = s.getOrDefault(ChainStep.GI.stream(), BigDecimal.ZERO);
                BigDecimal a = finished.getOrDefault("A", BigDecimal.ZERO), b = finished.getOrDefault("B", BigDecimal.ZERO);
                add(f, "Greige issued", issued);
                add(f, "Finished A", a);
                add(f, "Finished B", b);
                if (d.isBatchClosed() && issued.signum() > 0) add(f, "Loss", issued.subtract(a).subtract(b).max(BigDecimal.ZERO));
            }
            case RPI -> {
                add(f, "On delivery orders", s.get(ChainStep.DO.stream()));
                add(f, "Delivered", delivered);
            }
            case DO -> add(f, "Delivered", s.get(ChainStep.FD.stream()));
            default -> { }
        }
        return f;
    }

    private static void add(List<Map<String, Object>> f, String label, BigDecimal value) {
        f.add(Map.of("label", label, "value", value == null ? BigDecimal.ZERO : value));
    }


    /** The item and full fabric specification of a line, as the screens show it (blank values left out by the page). */
    static Map<String, Object> fabricOf(BusinessDocumentLineGroup g) {
        Map<String, Object> m = new LinkedHashMap<>();
        var item = g.getItem();
        if (item != null) {
            m.put("itemId", item.getId());
            m.put("itemCode", item.getItemCode());
            m.put("itemName", item.getName());
        }
        FabricSpec f = g.getFabric();
        if (f == null) return m;
        m.put("construction", f.getConstruction());
        m.put("declaredConstruction", f.getDeclaredConstruction());
        m.put("fabricType", f.getFabricType());
        m.put("composition", f.getComposition());
        m.put("weaveType", f.getWeaveType());
        m.put("weaveStyle", f.getWeaveStyle());
        m.put("finishType", f.getFinishType());
        m.put("epi", f.getEpi());
        m.put("ppi", f.getPpi());
        m.put("warpCount", counts(f.getWarpCount1(), f.getWarpCountRatio1(), f.getWarpCount2(), f.getWarpCountRatio2(),
            f.getWarpCount3(), f.getWarpCountRatio3()));
        m.put("weftCount", counts(f.getWeftCount1(), f.getWeftCountRatio1(), f.getWeftCount2(), f.getWeftCountRatio2(),
            f.getWeftCount3(), f.getWeftCountRatio3()));
        m.put("warpYarnName", f.getWarpYarnName());
        m.put("weftYarnName", f.getWeftYarnName());
        m.put("finishWidth", f.getFinishWidth());
        m.put("cuttableWidth", f.getCuttableWidth());
        m.put("gsm", f.getGsm());
        m.put("gsmBeforeWash", f.getGsmBeforeWash());
        m.put("gsmAfterWash", f.getGsmAfterWash());
        m.put("shrinkage", joined(" × ", f.getShrinkageWarp(), f.getShrinkageWeft()));
        m.put("washType", f.getWashType());
        m.put("lightSource", f.getLightSource());
        m.put("selvedge", f.getSelvedge());
        m.put("endUse", f.getEndUse());
        m.put("dispoReference", f.getDispoReference());
        m.put("costingCode", f.getCostingCode());
        m.put("qualityReference", f.getQualityReference());
        m.put("styleReference", f.getStyleReference());
        m.put("itemDescription", f.getItemDescription());
        return m;
    }

    /** A colour line's colour detail. */
    static Map<String, Object> colourOf(BusinessDocumentColorLine l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("colorName", l.getColorName());
        m.put("colorCode", l.getColorCode());
        m.put("fabricsStyle", l.getFabricsStyle());
        m.put("colorReference", l.getColorReference());
        m.put("strikeOffReference", l.getStrikeOffReference());
        m.put("labDip", l.getLabDipReference());
        m.put("loomReference", l.getLoomReference());
        m.put("colorSpecification", l.getColorSpecification());
        return m;
    }

    /** The colours of a fabric line, for lines that take the whole line at once (greige woven per construction). */
    static List<Map<String, Object>> coloursOf(BusinessDocumentLineGroup g) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BusinessDocumentColorLine c : g.getColorLines()) {
            Map<String, Object> m = colourOf(c);
            m.put("quantity", c.getQuantity());
            out.add(m);
        }
        return out;
    }

    /** Yarn counts with their ratios: "30" or "30 ×2 + 40 ×1". */
    static String counts(String c1, BigDecimal r1, String c2, BigDecimal r2, String c3, BigDecimal r3) {
        String[] cs = {c1, c2, c3};
        BigDecimal[] rs = {r1, r2, r3};
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (cs[i] == null || cs[i].isBlank()) continue;
            parts.add(cs[i].trim());
        }
        if (parts.size() <= 1) return parts.isEmpty() ? null : parts.get(0);
        parts.clear();
        for (int i = 0; i < 3; i++) {
            if (cs[i] == null || cs[i].isBlank()) continue;
            parts.add(rs[i] == null || rs[i].signum() == 0 ? cs[i].trim() : cs[i].trim() + " ×" + rs[i].stripTrailingZeros().toPlainString());
        }
        return String.join(" + ", parts);
    }

    private static String joined(String sep, String... values) {
        List<String> parts = Arrays.stream(values).filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
        return parts.isEmpty() ? null : String.join(sep, parts);
    }

    /** A unit as people read it: its symbol (yd, m), else its name. */
    static String uomLabel(com.asg.fabricerp.inventory.item.UnitOfMeasure u) {
        return u.getSymbol() != null && !u.getSymbol().isBlank() ? u.getSymbol() : u.getName();
    }
}
