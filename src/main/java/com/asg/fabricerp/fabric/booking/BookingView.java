package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.party.Party;
import com.asg.fabricerp.security.FabricUser;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.asg.fabricerp.common.AuditableEntity.idOf;

/**
 * A booking as JSON, for the grid, the review drawer and the editor.
 *
 * <p>Call inside the service's transaction: buyer, brand, garments and marketing person are
 * lazy, and this reads their names. LinkedHashMap throughout, not Map.of - the drawer renders
 * keys in declaration order, and Map.of neither keeps order nor accepts nulls.
 */
final class BookingView {

    private BookingView() { }

    static Map<String, Object> row(BusinessDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("documentNo", d.getDocumentNo());
        row.put("documentDate", d.getDocumentDate() == null ? "" : d.getDocumentDate().toString());
        row.put("requiredDate", d.getRequiredDate() == null ? "" : d.getRequiredDate().toString());
        row.put("partyName", nameOf(d.getParty()));
        row.put("marketingTeamName", d.getMarketingTeam() == null ? null : d.getMarketingTeam().getName());
        row.put("garmentsName", nameOf(d.getGarments()));
        row.put("referenceNo", d.getReferenceNo() == null ? "" : d.getReferenceNo());
        row.put("currency", d.getCurrencyCode());
        row.put("totalQuantity", d.getTotalQuantity());
        row.put("subtotalAmount", d.getSubtotalAmount());
        row.put("revisionNo", d.getRevisionNo());
        row.put("status", d.getStatus().name());
        row.put("editable", d.getStatus().isEditable());
        return row;
    }

    static Map<String, Object> detail(BusinessDocument d) {
        Map<String, Object> detail = row(d);
        detail.put("partyId", idOf(d.getParty()));
        detail.put("marketingTeamId", idOf(d.getMarketingTeam()));
        detail.put("bookingType", d.getBookingType() == null ? null : d.getBookingType().name());
        detail.put("bookingTypeLabel", d.getBookingType() == null ? null : d.getBookingType().label());
        detail.put("orderType", d.getOrderType() == null ? null : d.getOrderType().name());
        detail.put("orderTypeLabel", d.getOrderType() == null ? null : d.getOrderType().label());
        detail.put("brandId", idOf(d.getBrand()));
        detail.put("brandName", nameOf(d.getBrand()));
        detail.put("garmentsId", idOf(d.getGarments()));
        detail.put("garmentsAddress", d.getGarmentsAddress());
        detail.put("preCostBuyer", d.getPreCostBuyer());
        detail.put("priceInMeter", d.isPriceInMeter());
        FabricUser person = d.getMarketingPerson();
        detail.put("marketingPersonId", idOf(person));
        detail.put("marketingPersonName", person == null ? null : person.getFullName());
        detail.put("remarks", d.getRemarks() == null ? "" : d.getRemarks());
        detail.put("lineGroups", d.getLineGroups().stream().map(BookingView::group).toList());
        detail.put("terms", d.getTerms().stream().map(t -> {
            Map<String, Object> term = new LinkedHashMap<>();
            term.put("serialNo", t.getSerialNo());
            term.put("bodyText", t.getBodyText());
            return term;
        }).toList());
        return detail;
    }

    /**
     * One fabric specification, with its colour breakdown nested inside it — the shape a
     * real Booking API response actually has (one {@code dtlSet} carrying an array of
     * {@code dtlLine} colours).
     */
    static Map<String, Object> group(BusinessDocumentLineGroup g) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", g.getId());
        row.put("groupNo", g.getGroupNo());
        row.put("itemId", idOf(g.getItem()));
        row.put("itemName", g.getItem() == null ? null : g.getItem().getName());
        row.putAll(spec(g.getFabric()));
        row.put("groupQuantity", g.groupQuantity());
        row.put("groupAmount", g.groupAmount());
        row.put("colorLines", g.getColorLines().stream().map(BookingView::colorLine).toList());
        return row;
    }

    /** Every {@link FabricSpec} property under its own name, so the editor binds field for field. */
    static Map<String, Object> spec(FabricSpec f) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("costingCode", f.getCostingCode());
        s.put("costingAmendmentNo", f.getCostingAmendmentNo());
        s.put("fabricType", f.getFabricType());
        s.put("fabricSource", f.getFabricSource());
        s.put("finishType", f.getFinishType());
        s.put("finishTypeRef", f.getFinishTypeRef());
        s.put("qualityReference", f.getQualityReference());
        s.put("styleReference", f.getStyleReference());
        s.put("dispoReference", f.getDispoReference());
        s.put("quotedPrice", f.getQuotedPrice());
        s.put("breakEvenPrice", f.getBreakEvenPrice());
        s.put("warpCount1", f.getWarpCount1());
        s.put("warpCount2", f.getWarpCount2());
        s.put("warpCount3", f.getWarpCount3());
        s.put("warpCountRatio1", f.getWarpCountRatio1());
        s.put("warpCountRatio2", f.getWarpCountRatio2());
        s.put("warpCountRatio3", f.getWarpCountRatio3());
        s.put("weftCount1", f.getWeftCount1());
        s.put("weftCount2", f.getWeftCount2());
        s.put("weftCount3", f.getWeftCount3());
        s.put("weftCountRatio1", f.getWeftCountRatio1());
        s.put("weftCountRatio2", f.getWeftCountRatio2());
        s.put("weftCountRatio3", f.getWeftCountRatio3());
        s.put("epi", f.getEpi());
        s.put("ppi", f.getPpi());
        s.put("construction", f.getConstruction());
        s.put("declaredConstruction", f.getDeclaredConstruction());
        s.put("composition", f.getComposition());
        s.put("declaredComposition", f.getDeclaredComposition());
        s.put("warpYarnName", f.getWarpYarnName());
        s.put("weftYarnName", f.getWeftYarnName());
        s.put("weaveType", f.getWeaveType());
        s.put("weaveStyle", f.getWeaveStyle());
        s.put("finishWidth", f.getFinishWidth());
        s.put("cuttableWidth", f.getCuttableWidth());
        s.put("shrinkageWarp", f.getShrinkageWarp());
        s.put("shrinkageWeft", f.getShrinkageWeft());
        s.put("shrinkageMechanical", f.getShrinkageMechanical());
        s.put("gsm", f.getGsm());
        s.put("gsmBeforeWash", f.getGsmBeforeWash());
        s.put("gsmAfterWash", f.getGsmAfterWash());
        s.put("lightSource", f.getLightSource());
        s.put("lightSourceType", f.getLightSourceType());
        s.put("baseMaterial", f.getBaseMaterial());
        s.put("swatchNo", f.getSwatchNo());
        s.put("lcTenure", f.getLcTenure());
        s.put("lcPaymentType", f.getLcPaymentType());
        s.put("washType", f.getWashType());
        s.put("washInstruction", f.getWashInstruction());
        s.put("leadTimeDays", f.getLeadTimeDays());
        s.put("targetQualityParameter", f.getTargetQualityParameter());
        s.put("endUse", f.getEndUse());
        s.put("itemDescription", f.getItemDescription());
        return s;
    }

    static Map<String, Object> colorLine(BusinessDocumentColorLine l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", l.getId());
        row.put("colorLineNo", l.getColorLineNo());
        row.put("colorCode", l.getColorCode());
        row.put("colorName", l.getColorName());
        row.put("fabricsStyle", l.getFabricsStyle());
        row.put("colorReference", l.getColorReference());
        row.put("strikeOffReference", l.getStrikeOffReference());
        row.put("labDipReference", l.getLabDipReference());
        row.put("loomReference", l.getLoomReference());
        row.put("quantity", l.getQuantity());
        row.put("rate", l.getRate());
        row.put("priceInMeter", l.getPriceInMeter());
        row.put("lineAmount", l.getLineAmount());
        row.put("fulfilled", l.getFulfilledQuantity());
        row.put("outstanding", l.outstandingQuantity());
        row.put("remarks", l.getRemarks());
        return row;
    }

    /**
     * The legacy form's read-only Construction: warp count X weft count / EPI X PPI, e.g.
     * "34X20/78X48". Null until all four are known.
     */
    static String construction(FabricSpec f) {
        if (isBlank(f.getWarpCount1()) || isBlank(f.getWeftCount1()) || f.getEpi() == null || f.getPpi() == null) {
            return null;
        }
        return "%sX%s/%sX%s".formatted(f.getWarpCount1().trim(), f.getWeftCount1().trim(), plain(f.getEpi()), plain(f.getPpi()));
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String nameOf(Party p) {
        return p == null ? null : p.getName();
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
