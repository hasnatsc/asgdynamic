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
    @JsonProperty("dyeing_color_qty") String dyeingColorQtyRaw
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
