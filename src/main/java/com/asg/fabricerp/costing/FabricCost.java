package com.asg.fabricerp.costing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Typed projection of the external costing API's "data" object.
 *
 * The upstream returns 159 fields and sends every numeric as a JSON string, so money and
 * quantities bind to BigDecimal, never double. Only the fields this ERP consumes are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FabricCost(

    String code,
    @JsonProperty("amendment_no") String amendmentNo,
    String costingType,
    String costType,
    String status,

    String buyerName,
    String composition,
    String declaredComposition,
    String construction,
    String declaredConstruction,

    BigDecimal gsm,
    BigDecimal width,
    BigDecimal cuttableWidth,
    BigDecimal epi,
    BigDecimal ppi,
    BigDecimal conversionRate,

    BigDecimal warpYarnConsumption,
    BigDecimal weftYarnConsumption,
    BigDecimal yarnWillConsumed,

    BigDecimal totalYarnCost,
    BigDecimal weaveCost,
    BigDecimal processCostUsd,
    BigDecimal breakEvenPriceYds,
    BigDecimal breakEvenPriceMtr,
    BigDecimal piQuotPrice,

    BigDecimal moq,
    BigDecimal orderQty,

    @JsonProperty("final_approval") String finalApproval,

    /** Bridge into planning: the costing system hands off to BPO through these. */
    String planningFlag,
    String dispo,
    @JsonProperty("bpo_qty") BigDecimal bpoQty,

    // Nested payloads that arrive as JSON *strings* and need a second parse.
    @JsonProperty("fabric_cpmposition") String fabricCompositionRaw,  // sic: upstream misspelling
    @JsonProperty("yarn_details") String yarnDetailsRaw,
    @JsonProperty("dyeing_color_qty") String dyeingColorQtyRaw,

    // --- what the Booking form fills from a costing (verified against 18102503583) ---

    /** The costing's own quality reference, e.g. "D-3443". */
    String referenceNo,
    /** Fabric type as an id into the legacy {@code so_dtlSet_fabricsType} list (8 = Solid Dyed Print). */
    @JsonProperty("fabric") String fabricTypeId,
    /** "In-house" or "Export" - named fabricType upstream, but it is where the fabric is made. */
    @JsonProperty("fabricType") String fabricSource,
    /** Id into {@code so_dtlSet_weaveType} (1 = 1/1, 7 = Dobby : Medium Float). */
    @JsonProperty("weaveType") String weaveTypeId,
    /** Id into {@code so_dtlSet_weaveStyles} (5 = Plain, 10 = Medium Float). */
    @JsonProperty("weaveStyle") String weaveStyleId,
    List<String> fabricFinishTypeString,

    @JsonProperty("warpCount_1") String warpCount1,
    @JsonProperty("warpCount_2") String warpCount2,
    @JsonProperty("warpCount_3") String warpCount3,
    @JsonProperty("warpCountRatio_1") BigDecimal warpCountRatio1,
    @JsonProperty("warpCountRatio_2") BigDecimal warpCountRatio2,
    @JsonProperty("warpCountRatio_3") BigDecimal warpCountRatio3,
    @JsonProperty("weftCount_1") String weftCount1,
    @JsonProperty("weftCount_2") String weftCount2,
    @JsonProperty("weftCount_3") String weftCount3,
    @JsonProperty("weftCountRatio_1") BigDecimal weftCountRatio1,
    @JsonProperty("weftCountRatio_2") BigDecimal weftCountRatio2,
    @JsonProperty("weftCountRatio_3") BigDecimal weftCountRatio3,

    String warpShrinkage,
    String weftShrinkage,
    String mechShrinkage,

    /** Id into {@code so_dtlSet_lcTenure} (4 = 90 Days). */
    @JsonProperty("LcType") String lcTenureId,

    /**
     * The price per yard quoted to the buyer - what the legacy Booking form shows as
     * "Quot Price Costing" (3.95 on 18102503583). Despite the name it is not a markup, and it is
     * not {@link #piQuotPrice} (4.31972 on the same costing), which that form never displays.
     */
    BigDecimal piMarkup,

    /** Free text from costing / R&amp;D, e.g. the shrinkage risk the booking should allow for. */
    String note
) {

    /** True once signed off upstream — safe to cache indefinitely. */
    public boolean isFinallyApproved() {
        return "Approved".equalsIgnoreCase(finalApproval);
    }

    public record Composition(String count, String fiber, BigDecimal blend, BigDecimal resultant) {}

    public record ColourQty(String colour, BigDecimal qty) {}

    /** Zips the parallel colour[]/qty[] arrays the upstream sends. */
    public static List<ColourQty> zipColourQty(List<String> colours, List<BigDecimal> qty) {
        int n = Math.min(colours == null ? 0 : colours.size(), qty == null ? 0 : qty.size());
        return IntStream.range(0, n)
            .mapToObj(i -> new ColourQty(colours.get(i), qty.get(i)))
            .toList();
    }
}
