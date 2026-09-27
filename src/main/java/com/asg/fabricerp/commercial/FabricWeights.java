package com.asg.fabricerp.commercial;

import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.global.documents.FabricSpec;
import com.asg.fabricerp.inventory.item.UnitOfMeasure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The export PI's calculated net and gross fabric weight - the legacy {@code computeFabricWeights},
 * moved to the server so the PI, the LC and the packing list all print the same kilos.
 *
 * <pre>
 *   resultant count  Σcounts × Σratios / Σ(Σcounts × ratio / count)      for warp and for weft
 *   constant         (EPI / warp count + PPI / weft count) × 0.024412
 *   net kg           length (m) × finished width (m) × constant
 *   gross kg         net × 1.03
 * </pre>
 *
 * Counts read as the legacy screen read them: {@code 30} or {@code 30/1} Ne, {@code 30/2} as 15 Ne,
 * {@code 40D} denier as 5315 / 40 Ne, {@code 30/1+40D} (core-spun) by its cotton count.
 *
 * <p><b>One correction.</b> The legacy script divided by the sum of the ratios, so a count entered
 * without a ratio - the usual single-count yarn - gave a weight of "Infinity" on the PI. A count
 * with no ratio now counts as ratio 1.
 */
public final class FabricWeights {

    /** Kilos a length of fabric weighs per (EPI/count + PPI/count) per square metre, as the legacy constant. */
    static final double CONSTANT = 0.024412;
    static final double GROSS_FACTOR = 1.03;
    static final double METRES_PER_YARD = 0.9144;
    static final double METRES_PER_INCH = 0.0254;

    private FabricWeights() { }

    /** Net and gross kilos, and the fabric lines that could not be weighed (their spec is incomplete). */
    public record Result(BigDecimal net, BigDecimal gross, List<Integer> unweighed) { }

    public static Result of(List<BusinessDocumentLineGroup> groups) {
        double net = 0;
        List<Integer> unweighed = new java.util.ArrayList<>();
        for (BusinessDocumentLineGroup g : groups) {
            double kilos = weigh(g.getFabric(), g.groupQuantity().doubleValue(), inMetres(g.getUom()));
            if (Double.isNaN(kilos)) unweighed.add(g.getGroupNo());
            else net += kilos;
        }
        BigDecimal n = BigDecimal.valueOf(net).setScale(3, RoundingMode.HALF_UP);
        return new Result(n, BigDecimal.valueOf(net * GROSS_FACTOR).setScale(3, RoundingMode.HALF_UP), unweighed);
    }

    /** One fabric line's net kilos, or NaN when its construction is not complete enough to weigh. */
    static double weigh(FabricSpec f, double quantity, boolean metres) {
        double warp = resultant(new String[] {f.getWarpCount1(), f.getWarpCount2(), f.getWarpCount3()},
            new BigDecimal[] {f.getWarpCountRatio1(), f.getWarpCountRatio2(), f.getWarpCountRatio3()});
        double weft = resultant(new String[] {f.getWeftCount1(), f.getWeftCount2(), f.getWeftCount3()},
            new BigDecimal[] {f.getWeftCountRatio1(), f.getWeftCountRatio2(), f.getWeftCountRatio3()});
        if (warp <= 0 || weft <= 0 || f.getEpi() == null || f.getPpi() == null || f.getFinishWidth() == null) return Double.NaN;
        double constant = (f.getEpi().doubleValue() / warp + f.getPpi().doubleValue() / weft) * CONSTANT;
        double length = metres ? quantity : quantity * METRES_PER_YARD;
        double width = f.getFinishWidth().doubleValue() * METRES_PER_INCH;
        return length * width * constant;
    }

    /** The resultant count of up to three yarns laid in the given ratio. */
    static double resultant(String[] counts, BigDecimal[] ratios) {
        double[] c = new double[3];
        double[] r = new double[3];
        double sumCounts = 0, sumRatios = 0;
        for (int i = 0; i < 3; i++) {
            c[i] = count(counts[i]);
            if (c[i] <= 0) continue;
            r[i] = ratios[i] != null && ratios[i].signum() > 0 ? ratios[i].doubleValue() : 1;
            sumCounts += c[i];
            sumRatios += r[i];
        }
        double down = 0;
        for (int i = 0; i < 3; i++) if (c[i] > 0) down += sumCounts * r[i] / c[i];
        return down > 0 ? sumCounts * sumRatios / down : 0;
    }

    /** A yarn count as English cotton count (Ne). */
    static double count(String value) {
        if (value == null || value.isBlank()) return 0;
        String s = value.strip().toUpperCase();
        if (s.contains("D")) {
            if (s.contains("+")) {
                int dPlus = s.indexOf("D+");
                double denierPart = dPlus > 0 ? leading(s.substring(0, dPlus)) : 0;
                return denierPart > 0 ? denierPart : leading(s.substring(0, s.indexOf('+')));
            }
            double denier = leading(s.substring(0, s.indexOf('D')));
            return denier > 0 ? 5315 / denier : 0;
        }
        if (s.contains("/")) {
            String[] parts = s.split("/");
            double a = leading(parts[0]), b = parts.length > 1 ? leading(parts[1]) : 1;
            return b > 0 ? a / b : 0;
        }
        String digits = s.replaceAll("[^0-9.]", "");
        try {
            return digits.isEmpty() ? 0 : Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** JavaScript's parseInt: the leading whole number, or 0. */
    private static double leading(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d+)").matcher(s);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    private static boolean inMetres(UnitOfMeasure u) {
        if (u == null) return false;
        String code = (u.getSymbol() == null ? "" : u.getSymbol()) + "|" + (u.getName() == null ? "" : u.getName());
        return code.toLowerCase().matches("(m|mtr|meter|metre)\\|.*|.*\\|(meter|metre)s?");
    }
}
