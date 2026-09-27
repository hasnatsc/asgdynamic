package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.*;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.inventory.item.InventoryItem;
import com.asg.fabricerp.inventory.item.UnitOfMeasure;
import com.asg.fabricerp.security.AuthorityChecks;
import com.asg.fabricerp.supply.SupplyDraws;
import com.asg.fabricerp.supply.SupplyQueries;
import org.hibernate.Hibernate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asg.fabricerp.common.AuditableEntity.idOf;

/**
 * The JSON the commercial screens read: a grid row, and a document's full detail - header,
 * commercial facts with names resolved, lines with where each stands downstream, what has been
 * recorded against it, a CI's realization, and what may be done to it now.
 */
@Component
public class CommercialViews {

    private static final Set<BusinessDocumentStatus> OPEN = EnumSet.of(BusinessDocumentStatus.APPROVED,
        BusinessDocumentStatus.PROCESSING, BusinessDocumentStatus.PARTIAL);

    private final CommercialDocumentService documents;
    private final CommercialEventRepository events;
    private final SupplyQueries supplyQueries;
    private final NamedParameterJdbcTemplate jdbc;

    public CommercialViews(CommercialDocumentService documents, CommercialEventRepository events, SupplyQueries supplyQueries,
                           NamedParameterJdbcTemplate jdbc) {
        this.documents = documents;
        this.events = events;
        this.supplyQueries = supplyQueries;
        this.jdbc = jdbc;
    }

    // ----------------------------------------------------------------------------------- rows

    public static Map<String, Object> header(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("documentType", d.getDocumentType().name());
        row.put("documentDate", d.getDocumentDate());
        BusinessDocument parent = d.getParentDocument();
        row.put("parentId", idOf(parent));
        row.put("parentNo", parent != null && Hibernate.isInitialized(parent) ? parent.getDocumentNo() : null);
        row.put("partyId", idOf(d.getParty()));
        row.put("partyName", d.getParty() != null && Hibernate.isInitialized(d.getParty()) ? d.getParty().getName() : null);
        row.put("brandName", d.getBrand() != null && Hibernate.isInitialized(d.getBrand()) ? d.getBrand().getName() : null);
        row.put("garmentsName", d.getGarments() != null && Hibernate.isInitialized(d.getGarments()) ? d.getGarments().getName() : null);
        row.put("marketingTeamName", d.getMarketingTeam() != null && Hibernate.isInitialized(d.getMarketingTeam()) ? d.getMarketingTeam().getName() : null);
        row.put("currency", d.getCurrencyCode());
        row.put("exchangeRate", d.getExchangeRate());
        row.put("referenceNo", d.getReferenceNo());
        row.put("remarks", d.getRemarks());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("subtotalAmount", d.getSubtotalAmount());
        row.put("revisionNo", d.getRevisionNo());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> gridRow(BusinessDocument d) {
        if (d.getParentDocument() != null) Hibernate.initialize(d.getParentDocument());
        if (d.getParty() != null) Hibernate.initialize(d.getParty());
        if (d.getBrand() != null) Hibernate.initialize(d.getBrand());
        if (d.getMarketingTeam() != null) Hibernate.initialize(d.getMarketingTeam());
        Map<String, Object> row = header(d);
        CommercialDetails det = documents.details(d);
        row.put("lcNo", det.getLcNo());
        row.put("validityDate", det.getValidityDate());
        row.put("ciKind", det.getCiKind() == null ? null : det.getCiKind().label());
        row.put("realizationStep", det.getRealizationStep() == null ? null : det.getRealizationStep().label());
        return row;
    }

    /** One line as the editor and the viewer show it: fabric and colour, or item. */
    public static Map<String, Object> lineOf(BusinessDocumentColorLine l) {
        BusinessDocumentLineGroup g = l.getLineGroup();
        FabricSpec f = g.getFabric();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("groupNo", g.getGroupNo());
        m.put("documentId", g.getDocument().getId());
        m.put("documentNo", g.getDocument().getDocumentNo());
        InventoryItem item = g.getItem();
        m.put("itemId", item == null ? null : item.getId());
        m.put("itemCode", item == null ? null : item.getItemCode());
        m.put("itemName", item == null ? null : item.getName());
        m.put("itemType", item == null ? null : item.getItemType());
        m.put("uom", uomLabel(g.getUom()));
        m.put("specification", g.getItemSpecification());
        m.put("construction", f.getConstruction());
        m.put("composition", f.getComposition());
        m.put("fabricType", f.getFabricType());
        m.put("weave", String.join(" ", Arrays.stream(new String[] {f.getWeaveType(), f.getWeaveStyle()}).filter(Objects::nonNull).toList()));
        m.put("finishWidth", f.getFinishWidth());
        m.put("gsm", f.getGsm());
        m.put("colorName", l.getColorName());
        m.put("colorCode", l.getColorCode());
        m.put("fabricsStyle", l.getFabricsStyle());
        m.put("deliveryDate", l.getDeliveryDate());
        m.put("quantity", l.getQuantity());
        m.put("rate", l.getRate());
        m.put("priceInMeter", l.getPriceInMeter());
        m.put("lineAmount", l.getLineAmount());
        m.put("remarks", l.getRemarks());
        m.put("shortClosed", l.isShortClosed());
        return m;
    }

    // --------------------------------------------------------------------------------- detail

    @Transactional(readOnly = true)
    public Map<String, Object> detail(CommercialStep step, Long id) {
        BusinessDocument d = documents.get(step, id);
        Map<String, Object> out = gridRow(d);
        CommercialDetails det = documents.details(d);
        out.put("step", step.name());
        out.put("kind", step.label());
        out.put("revisionOfId", idOf(d.getRevisionOf()));
        BusinessDocument parent = d.getParentDocument();
        if (parent != null) {
            out.put("parent", Map.of("id", parent.getId(), "documentNo", parent.getDocumentNo(),
                "slug", slugOf(parent.getDocumentType()), "label", labelOf(parent.getDocumentType())));
        }
        out.put("details", facts(det));

        boolean maker = AuthorityChecks.holds(step.authority("CREATE")) || AuthorityChecks.holds(step.authority("AMEND"));
        boolean amender = AuthorityChecks.holds(step.authority("AMEND"));
        BusinessDocumentStatus s = d.getStatus();
        boolean live = s != BusinessDocumentStatus.CANCELLED && s != BusinessDocumentStatus.CLOSED;
        out.put("editable", maker && s.isEditable());
        out.put("deletable", s.isEditable() && AuthorityChecks.holds(step.authority("DELETE")));
        out.put("submittable", s.isEditable() && maker);
        out.put("cancellable", amender && live && s != BusinessDocumentStatus.SUBMITTED && s != BusinessDocumentStatus.COMPLETED);
        out.put("closable", amender && s == BusinessDocumentStatus.COMPLETED);
        out.put("revisable", amender && step.isRevisable() && s.isCommitted() && s != BusinessDocumentStatus.CLOSED);
        out.put("canRecord", amender && live);
        out.put("open", OPEN.contains(s));

        // Lines, and where each stands downstream.
        List<BusinessDocumentColorLine> lines = SupplyDraws.lines(d);
        List<Long> ids = lines.stream().map(BusinessDocumentColorLine::getId).toList();
        DocumentType next = d.getDocumentType().fulfilledBy();
        Map<Long, BigDecimal> done = next == null ? Map.of() : supplyQueries.committed(next, ids);
        Map<Long, BigDecimal> onLc = step == CommercialStep.IPI ? supplyQueries.committed(DocumentType.IMPORT_LETTER_OF_CREDIT, ids) : Map.of();
        String doneLabel = switch (step) {
            case EPI -> "On LC";
            case ELC -> "Invoiced";
            case IPI -> "Ordered";
            default -> null;
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BusinessDocumentColorLine l : lines) {
            Map<String, Object> row = lineOf(l);
            BusinessDocumentColorLine src = l.getSourceColorLine();
            row.put("sourceId", idOf(src));
            row.put("sourceLabel", src == null ? null : "%s · %s".formatted(src.getLineGroup().getDocument().getDocumentNo(), SupplyDraws.lineName(src)));
            row.put("sourceDocumentId", src == null ? null : src.getLineGroup().getDocument().getId());
            BusinessDocumentColorLine challan = l.getDeliveryLine();
            row.put("deliveryLineId", idOf(challan));
            row.put("challanNo", challan == null ? null : challan.getLineGroup().getDocument().getDocumentNo());
            row.put("challanDate", challan == null ? null : challan.getLineGroup().getDocument().getDocumentDate());
            List<Map<String, Object>> figures = new ArrayList<>();
            if (doneLabel != null) {
                BigDecimal taken = done.getOrDefault(l.getId(), BigDecimal.ZERO);
                figures.add(figure(doneLabel, taken));
                if (step == CommercialStep.IPI) figures.add(figure("On import LC", onLc.getOrDefault(l.getId(), BigDecimal.ZERO)));
                if (s.isCommitted()) figures.add(figure("Open", l.getQuantity().subtract(taken).max(BigDecimal.ZERO)));
            }
            row.put("figures", figures);
            rows.add(row);
        }
        out.put("lines", rows);
        out.put("terms", d.getTerms().stream().map(BusinessDocumentTerm::getBodyText).toList());
        out.put("keyTerms", keyTerms(step, d, det));

        // What has been recorded against it.
        List<Map<String, Object>> recorded = events.findByDocumentIdOrderByEventDateAscIdAsc(d.getId()).stream().map(this::event).toList();
        out.put("events", recorded);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Map<String, Object> e : recorded) {
            if (e.get("amount") != null) totals.merge((String) e.get("kind"), (BigDecimal) e.get("amount"), BigDecimal::add);
        }
        out.put("eventTotals", totals);
        if (step == CommercialStep.ECI) out.put("realization", realization(d, det, recorded));
        if (step == CommercialStep.IPI) out.put("milestones", milestones(recorded));
        if (step == CommercialStep.ELC) {
            out.put("backToBack", backToBack(d.getId()));
            out.put("invoicedValue", sum("""
                SELECT COALESCE(SUM(d.subtotal_amount), 0) FROM gbl_business_documents d
                WHERE d.parent_document_id = :id AND d.document_type = 'EXPORT_COMMERCIAL_INVOICE' AND d.deleted = FALSE
                  AND d.status IN ('APPROVED', 'PARTIAL', 'PROCESSING', 'COMPLETED', 'CLOSED')""", d.getId()));
            out.put("realizedValue", sum("""
                SELECT COALESCE(SUM(e.amount), 0) FROM com_document_events e
                JOIN gbl_business_documents d ON d.id = e.document_id
                WHERE d.parent_document_id = :id AND d.document_type = 'EXPORT_COMMERCIAL_INVOICE' AND d.deleted = FALSE
                  AND d.status <> 'CANCELLED' AND e.kind = 'REALIZATION' AND e.code = 'FINAL_PAYMENT'""", d.getId()));
        }
        out.put("children", children(d.getId()));
        return out;
    }

    /** The facts, with the names of what they refer to. */
    private Map<String, Object> facts(CommercialDetails det) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("validityDate", det.getValidityDate());
        m.put("shipmentDate", det.getShipmentDate());
        m.put("issueDate", det.getIssueDate());
        m.put("lcNo", det.getLcNo());
        m.put("masterLcNo", det.getMasterLcNo());
        m.put("masterLcDate", det.getMasterLcDate());
        m.put("tenure", det.getTenure());
        m.put("tenureLabel", det.getTenure() == null ? null : det.getTenure().label());
        m.put("paymentTerms", det.getPaymentTerms());
        m.put("paymentTermsLabel", det.getPaymentTerms() == null ? null : det.getPaymentTerms().label());
        m.put("deliveryTerms", det.getDeliveryTerms());
        m.put("deliveryTermsLabel", det.getDeliveryTerms() == null ? null : det.getDeliveryTerms().label());
        m.put("bankId", det.getBankId());
        m.put("bankName", partyName(det.getBankId()));
        m.put("bankAccountId", det.getBankAccountId());
        m.put("bankAccount", accountLabel(det.getBankAccountId()));
        m.put("counterBankId", det.getCounterBankId());
        m.put("counterBankName", partyName(det.getCounterBankId()));
        m.put("counterBankAccountId", det.getCounterBankAccountId());
        m.put("counterBankAccount", accountLabel(det.getCounterBankAccountId()));
        m.put("foreignBankName", det.getForeignBankName());
        m.put("foreignBankBin", det.getForeignBankBin());
        m.put("foreignBankSwift", det.getForeignBankSwift());
        m.put("foreignBankRouting", det.getForeignBankRouting());
        m.put("beneficiaryAccountNo", det.getBeneficiaryAccountNo());
        m.put("hsCodeId", det.getHsCodeId());
        m.put("hsCode", det.getHsCodeId() == null ? null : jdbc.query("SELECT hs_code FROM inv_hs_codes WHERE id = :id",
            new MapSqlParameterSource("id", det.getHsCodeId()), rs -> rs.next() ? rs.getString(1) : null));
        m.put("applicantBondLicence", det.getApplicantBondLicence());
        m.put("netWeight", det.getNetWeight());
        m.put("grossWeight", det.getGrossWeight());
        m.put("calcNetWeight", det.getCalcNetWeight());
        m.put("calcGrossWeight", det.getCalcGrossWeight());
        m.put("amountInWords", det.getAmountInWords());
        m.put("partialShipment", det.isPartialShipment());
        m.put("btmaCertificate", det.isBtmaCertificate());
        m.put("acknowledgedOn", det.getAcknowledgedOn());
        m.put("ciKind", det.getCiKind());
        m.put("ciKindLabel", det.getCiKind() == null ? null : det.getCiKind().label());
        m.put("ibcNo", det.getIbcNo());
        m.put("realizationStep", det.getRealizationStep());
        m.put("importDocType", det.getImportDocType());
        m.put("lcType", det.getLcType());
        m.put("lcTypeLabel", det.getLcType() == null ? null : det.getLcType().label());
        m.put("port", det.getPort());
        m.put("cnfAgent", det.getCnfAgent());
        m.put("ipNo", det.getIpNo());
        m.put("sroBenefited", det.isSroBenefited());
        m.put("btmaNo", det.getBtmaNo());
        m.put("btmaDate", det.getBtmaDate());
        m.put("billOfEntryNo", det.getBillOfEntryNo());
        m.put("billOfEntryDate", det.getBillOfEntryDate());
        m.put("localAgentId", det.getLocalAgentId());
        m.put("localAgentName", partyName(det.getLocalAgentId()));
        m.put("backedByDocumentId", det.getBackedByDocumentId());
        m.put("backedByDocumentNo", det.getBackedByDocumentId() == null ? null : jdbc.query(
            "SELECT document_no FROM gbl_business_documents WHERE id = :id", new MapSqlParameterSource("id", det.getBackedByDocumentId()),
            rs -> rs.next() ? rs.getString(1) : null));
        m.put("incentiveAmount", det.getIncentiveAmount());
        m.put("incentiveAppliedOn", det.getIncentiveAppliedOn());
        m.put("incentiveConfirmedOn", det.getIncentiveConfirmedOn());
        return m;
    }

    /** The clauses the legacy PI printed from its own fields, above the library's terms. */
    private List<Map<String, String>> keyTerms(CommercialStep step, BusinessDocument d, CommercialDetails det) {
        List<Map<String, String>> out = new ArrayList<>();
        java.util.function.BiConsumer<String, Object> add = (k, v) -> { if (v != null && !String.valueOf(v).isBlank()) out.add(Map.of("title", k, "text", String.valueOf(v))); };
        if (step == CommercialStep.EPI || step == CommercialStep.IPI) add.accept("Offer validity", det.getValidityDate());
        if (step.isExport()) add.accept("Letter of credit", det.getTenure() == null ? null : "Irrevocable LC " + det.getTenure().label());
        add.accept("Payment", det.getPaymentTerms() == null ? null : det.getPaymentTerms().label());
        String bank = partyName(det.getBankId());
        String account = accountLabel(det.getBankAccountId());
        add.accept(step == CommercialStep.EPI ? "Advising bank" : "Bank", bank == null ? null : account == null ? bank : bank + ", " + account);
        add.accept("INCO terms", det.getDeliveryTerms() == null ? null : det.getDeliveryTerms().label());
        if (step == CommercialStep.EPI) {
            add.accept("H.S. code", facts(det).get("hsCode"));
            add.accept("Bond licence", det.getApplicantBondLicence());
        }
        if (det.getNetWeight() != null && det.getNetWeight().signum() > 0) add.accept("Net weight", det.getNetWeight().stripTrailingZeros().toPlainString() + " kg");
        if (det.getGrossWeight() != null && det.getGrossWeight().signum() > 0) add.accept("Gross weight", det.getGrossWeight().stripTrailingZeros().toPlainString() + " kg");
        return out;
    }

    private Map<String, Object> event(CommercialEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("kind", e.getKind().name());
        m.put("code", e.getCode());
        m.put("codeLabel", codeLabel(e));
        m.put("refNo", e.getRefNo());
        m.put("date", e.getEventDate());
        m.put("amount", e.getAmount());
        m.put("partyId", e.getPartyId());
        m.put("partyName", partyName(e.getPartyId()));
        m.put("costHeadId", e.getCostHeadId());
        m.put("costHead", e.getCostHeadId() == null ? null : jdbc.query("SELECT name FROM com_cost_heads WHERE id = :id",
            new MapSqlParameterSource("id", e.getCostHeadId()), rs -> rs.next() ? rs.getString(1) : null));
        m.put("documentNameId", e.getDocumentNameId());
        m.put("documentName", e.getDocumentNameId() == null ? null : jdbc.query("SELECT name FROM com_document_names WHERE id = :id",
            new MapSqlParameterSource("id", e.getDocumentNameId()), rs -> rs.next() ? rs.getString(1) : null));
        m.put("remarks", e.getRemarks());
        m.put("recordedBy", e.getRecordedBy());
        m.put("recordedAt", e.getRecordedAt());
        return m;
    }

    private static String codeLabel(CommercialEvent e) {
        if (e.getCode() == null) return null;
        try {
            return switch (e.getKind()) {
                case REALIZATION -> RealizationStep.valueOf(e.getCode()).label();
                case MILESTONE -> ImportMilestone.valueOf(e.getCode()).label();
                default -> e.getCode();
            };
        } catch (IllegalArgumentException unknown) {
            return e.getCode();
        }
    }

    /**
     * A CI's way to cash: every step, recorded or not, which may be recorded next, and when the bill
     * matures - acceptance (or delivery, negotiation, shipment, as the payment terms say) plus tenure.
     */
    private Map<String, Object> realization(BusinessDocument d, CommercialDetails det, List<Map<String, Object>> recorded) {
        Map<String, Map<String, Object>> byCode = new HashMap<>();
        recorded.stream().filter(e -> "REALIZATION".equals(e.get("kind"))).forEach(e -> byCode.put((String) e.get("code"), e));
        List<Map<String, Object>> steps = new ArrayList<>();
        boolean committed = d.getStatus().isCommitted() && d.getStatus() != BusinessDocumentStatus.CLOSED;
        for (RealizationStep step : RealizationStep.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", step.name());
            m.put("label", step.label());
            m.put("hasAmount", step.hasAmount());
            m.put("optional", step.isOptional());
            Map<String, Object> e = byCode.get(step.name());
            m.put("recorded", e != null);
            if (e != null) {
                m.put("eventId", e.get("id"));
                m.put("date", e.get("date"));
                m.put("amount", e.get("amount"));
                m.put("refNo", e.get("refNo"));
            }
            m.put("allowed", committed && e == null && !byCode.containsKey(RealizationStep.FINAL_PAYMENT.name())
                && step.required().stream().allMatch(r -> byCode.containsKey(r.name())));
            steps.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("steps", steps);
        out.put("maturityDue", maturityDue(det, byCode, d));
        return out;
    }

    static LocalDate maturityDue(CommercialDetails det, Map<String, Map<String, Object>> byCode, BusinessDocument d) {
        if (det.getTenure() == null) return null;
        Object start = switch (det.getPaymentTerms() == null ? PaymentTerms.DATE_OF_ACCEPTANCE : det.getPaymentTerms()) {
            case DATE_OF_ACCEPTANCE -> Optional.ofNullable(byCode.get("PARTY_ACCEPTANCE")).map(e -> e.get("date")).orElse(null);
            case DATE_OF_NEGOTIATION -> Optional.ofNullable(byCode.get("BANK_SUBMISSION")).map(e -> e.get("date")).orElse(null);
            case DATE_OF_DELIVERY, DATE_OF_SHIPMENT -> d.getDocumentDate();
            case AT_SIGHT, TT -> Optional.ofNullable(byCode.get("BANK_SUBMISSION")).map(e -> e.get("date")).orElse(null);
        };
        return start instanceof LocalDate date ? date.plusDays(det.getTenure().days()) : null;
    }

    private static List<Map<String, Object>> milestones(List<Map<String, Object>> recorded) {
        Map<String, Map<String, Object>> byCode = new HashMap<>();
        recorded.stream().filter(e -> "MILESTONE".equals(e.get("kind"))).forEach(e -> byCode.put((String) e.get("code"), e));
        return Arrays.stream(ImportMilestone.values()).map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", m.name());
            row.put("label", m.label());
            Map<String, Object> e = byCode.get(m.name());
            row.put("done", e != null);
            if (e != null) {
                row.put("eventId", e.get("id"));
                row.put("date", e.get("date"));
                row.put("by", e.get("recordedBy"));
                row.put("remarks", e.get("remarks"));
            }
            return row;
        }).toList();
    }

    /** Import LCs opened back-to-back against this export LC. */
    private List<Map<String, Object>> backToBack(Long lcId) {
        return jdbc.queryForList("""
            SELECT d.id, d.document_no AS "documentNo", c.lc_no AS "lcNo", p.name AS supplier, d.subtotal_amount AS amount,
                   d.currency_code AS currency, d.status, to_char(d.document_date, 'YYYY-MM-DD') AS "documentDate"
            FROM com_document_details c
            JOIN gbl_business_documents d ON d.id = c.document_id
            LEFT JOIN pty_parties p ON p.id = d.party_id
            WHERE c.backed_by_document_id = :id AND d.deleted = FALSE AND d.status <> 'CANCELLED'
            ORDER BY d.document_date, d.id
            """, new MapSqlParameterSource("id", lcId));
    }

    private List<Map<String, Object>> children(Long id) {
        return supplyQueries.childrenOf(id).stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>(c);
            DocumentType type = DocumentType.valueOf((String) c.get("documentType"));
            m.put("slug", slugOf(type));
            m.put("label", labelOf(type));
            return m;
        }).toList();
    }

    private BigDecimal sum(String sql, Long id) {
        return jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), BigDecimal.class);
    }

    private String partyName(Long id) {
        if (id == null) return null;
        return jdbc.query("SELECT name FROM pty_parties WHERE id = :id", new MapSqlParameterSource("id", id),
            rs -> rs.next() ? rs.getString(1) : null);
    }

    private String accountLabel(Long id) {
        if (id == null) return null;
        return jdbc.query("""
            SELECT a.account_number || ' (' || a.account_name || COALESCE(', ' || a.branch_name, '') || ')'
            FROM pty_party_bank_accounts a WHERE a.id = :id
            """, new MapSqlParameterSource("id", id), rs -> rs.next() ? rs.getString(1) : null);
    }

    private static Map<String, Object> figure(String label, BigDecimal value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        return m;
    }

    /** Where a document of this type lives - a commercial, store or production screen. */
    static String slugOf(DocumentType type) {
        return CommercialStep.of(type).map(CommercialStep::slug)
            .or(() -> com.asg.fabricerp.supply.SupplyStep.of(type).map(com.asg.fabricerp.supply.SupplyStep::slug))
            .or(() -> com.asg.fabricerp.production.ChainStep.of(type).map(com.asg.fabricerp.production.ChainStep::slug))
            .orElse("");
    }

    static String labelOf(DocumentType type) {
        return CommercialStep.of(type).map(CommercialStep::label)
            .or(() -> com.asg.fabricerp.supply.SupplyStep.of(type).map(com.asg.fabricerp.supply.SupplyStep::label))
            .or(() -> com.asg.fabricerp.production.ChainStep.of(type).map(com.asg.fabricerp.production.ChainStep::label))
            .orElse(type.label());
    }

    static String uomLabel(UnitOfMeasure u) {
        if (u == null) return null;
        return u.getSymbol() != null && !u.getSymbol().isBlank() ? u.getSymbol() : u.getCode();
    }
}
