package com.asg.fabricerp.costing;

import com.asg.fabricerp.global.documents.FabricSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * A costing, as the fabric specification a Booking line starts from.
 *
 * <p>Two jobs, deliberately separate:
 * <ul>
 *   <li>{@link #toSpec} - the full prefill for the editor when a costing number is entered.
 *       Every field is a suggestion the user may change: this is what the legacy screen's
 *       {@code onclick_so_dtlSet_fabricsCost} did in the browser, field for field.</li>
 *   <li>{@link #stamp} - what the server re-applies on every save, whatever the request said:
 *       the figures that belong to the costing and not to the booking (GSM, quoted and
 *       break-even price, amendment), plus any blank the user left that the costing can fill.</li>
 * </ul>
 *
 * <p>Mapping checked against costing 18102503583 and the legacy booking raised from it
 * (BKAF000030): construction, PI construction, composition, counts, EPI/PPI, widths, weave,
 * GSM 110, yarn names, LC tenure 90 Days and quoted price 3.95 all agree.
 */
@Component
public class CostingTranslator {

    private final ObjectMapper mapper;

    public CostingTranslator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public FabricSpec toSpec(FabricCost cost) {
        FabricSpec spec = new FabricSpec();
        spec.setCostingCode(cost.code());
        spec.setFabricType(CostingCatalog.fabricType(cost.fabricTypeId()));
        spec.setFabricSource(trim(cost.fabricSource()));
        spec.setFinishType(cost.fabricFinishTypeString() == null ? null
            : String.join(", ", cost.fabricFinishTypeString().stream().map(String::trim).toList()));

        spec.setWarpCount1(trim(cost.warpCount1()));
        spec.setWarpCount2(trim(cost.warpCount2()));
        spec.setWarpCount3(trim(cost.warpCount3()));
        spec.setWarpCountRatio1(cost.warpCountRatio1());
        spec.setWarpCountRatio2(cost.warpCountRatio2());
        spec.setWarpCountRatio3(cost.warpCountRatio3());
        spec.setWeftCount1(trim(cost.weftCount1()));
        spec.setWeftCount2(trim(cost.weftCount2()));
        spec.setWeftCount3(trim(cost.weftCount3()));
        spec.setWeftCountRatio1(cost.weftCountRatio1());
        spec.setWeftCountRatio2(cost.weftCountRatio2());
        spec.setWeftCountRatio3(cost.weftCountRatio3());
        spec.setEpi(cost.epi());
        spec.setPpi(cost.ppi());

        spec.setConstruction(trim(cost.construction()));
        spec.setDeclaredConstruction(trim(cost.declaredConstruction()));
        // The legacy form fills Composition with the costing's DECLARED composition - the
        // buyer-facing wording ("55%Linen+45% BCI Cotton"), not the resultant blend the
        // costing computed ("48.87% Cotton 51.13% Linen"). PI Composition gets the same.
        String declared = firstNonBlank(cost.declaredComposition(), cost.composition());
        spec.setComposition(declared);
        spec.setDeclaredComposition(declared);

        spec.setWeaveType(CostingCatalog.weaveType(cost.weaveTypeId()));
        spec.setWeaveStyle(CostingCatalog.weaveStyle(cost.weaveStyleId()));
        spec.setFinishWidth(cost.width());
        spec.setCuttableWidth(cost.cuttableWidth());
        spec.setShrinkageWarp(trim(cost.warpShrinkage()));
        spec.setShrinkageWeft(trim(cost.weftShrinkage()));
        spec.setShrinkageMechanical(trim(cost.mechShrinkage()));

        Map<String, Object> yarns = parseObject(cost.yarnDetailsRaw());
        spec.setWarpYarnName(trim(str(yarns.get("warpCountYarnName_1"))));
        spec.setWeftYarnName(trim(str(yarns.get("weftCountYarnName_1"))));
        spec.setLcTenure(CostingCatalog.lcTenure(cost.lcTenureId()));

        stamp(cost, spec);
        return spec;
    }

    /**
     * Re-applied on every save. Overwrites what belongs to the costing; fills, never overwrites,
     * what the user may legitimately have changed.
     */
    public void stamp(FabricCost cost, FabricSpec spec) {
        spec.setGsm(roundedGsm(cost.gsm()));
        spec.setQuotedPrice(cost.piMarkup());
        spec.setBreakEvenPrice(cost.breakEvenPriceYds());
        spec.setCostingAmendmentNo(trim(cost.amendmentNo()));

        if (isBlank(spec.getConstruction()))         spec.setConstruction(trim(cost.construction()));
        if (isBlank(spec.getDeclaredConstruction())) spec.setDeclaredConstruction(trim(cost.declaredConstruction()));
        if (isBlank(spec.getComposition()))          spec.setComposition(firstNonBlank(cost.declaredComposition(), cost.composition()));
        if (isBlank(spec.getDeclaredComposition()))  spec.setDeclaredComposition(firstNonBlank(cost.declaredComposition(), cost.composition()));
        if (spec.getFinishWidth() == null)           spec.setFinishWidth(cost.width());
        if (spec.getCuttableWidth() == null)         spec.setCuttableWidth(cost.cuttableWidth());
        if (spec.getEpi() == null)                   spec.setEpi(cost.epi());
        if (spec.getPpi() == null)                   spec.setPpi(cost.ppi());
    }

    /**
     * The costing's colour plan - {@code {"color":[...],"qty":[...]}} as two parallel arrays in a
     * JSON string. The legacy bookings raised from 18102503583 carry exactly these quantities
     * (10500 / 5230 / 2920) under the colour names marketing then wrote in.
     */
    public List<FabricCost.ColourQty> colours(FabricCost cost) {
        Map<String, Object> plan = parseObject(cost.dyeingColorQtyRaw());
        List<String> names = list(plan.get("color")).stream().map(o -> trim(str(o))).toList();
        List<BigDecimal> qty = list(plan.get("qty")).stream().map(CostingTranslator::dec).toList();
        return FabricCost.zipColourQty(names, qty).stream()
            .filter(c -> !isBlank(c.colour()) || c.qty() != null)
            .toList();
    }

    /** The legacy form shows the costing's GSM as a whole number (109.84 -> 110). */
    static BigDecimal roundedGsm(BigDecimal gsm) {
        return gsm == null ? null : gsm.setScale(0, RoundingMode.HALF_UP);
    }

    private Map<String, Object> parseObject(String raw) {
        if (isBlank(raw)) return Map.of();
        try {
            Map<String, Object> m = mapper.readValue(raw, new TypeReference<>() {});
            return m == null ? Map.of() : m;
        } catch (Exception e) {
            // A malformed nested blob loses the fields it carries, not the whole prefill.
            return Map.of();
        }
    }

    private static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static BigDecimal dec(Object o) {
        if (o == null) return null;
        try {
            String s = o.toString().trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? trim(a) : trim(b);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
