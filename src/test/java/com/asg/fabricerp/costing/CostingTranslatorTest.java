package com.asg.fabricerp.costing;

import com.asg.fabricerp.global.documents.FabricSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The costing mapping, pinned to real payloads rather than hand-made ones.
 *
 * <p>Expected values are what the legacy Booking screen showed for the same costings:
 * 18102503583 was booked as BKAF000030 (34X20/78X48, GSM 110, Quot Price 3.95, 90 Days), and
 * 17072502459 as a line of BKAF000024 (Dobby : Medium Float / Medium Float).
 */
class CostingTranslatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CostingTranslator translator = new CostingTranslator(mapper);

    private FabricCost load(String code) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/costing/" + code + ".json")) {
            JsonNode data = mapper.readTree(in).get("data");
            return mapper.treeToValue(data, FabricCost.class);
        }
    }

    @Test
    void fillsTheSpecificationTheLegacyFormShowedFor18102503583() throws Exception {
        FabricSpec spec = translator.toSpec(load("18102503583"));

        assertThat(spec.getCostingCode()).isEqualTo("18102503583");
        assertThat(spec.getCostingAmendmentNo()).isEqualTo("0");
        assertThat(spec.getFabricType()).isEqualTo("Solid Dyed Print");
        assertThat(spec.getFabricSource()).isEqualTo("In-house");
        assertThat(spec.getFinishType()).isEqualTo("Soft Finish");

        assertThat(spec.getWarpCount1()).isEqualTo("34");
        assertThat(spec.getWarpCountRatio1()).isEqualByComparingTo("1");
        assertThat(spec.getWeftCount1()).isEqualTo("20");
        assertThat(spec.getWeftCountRatio1()).isEqualByComparingTo("1");
        assertThat(spec.getWarpCount2()).isNull();
        assertThat(spec.getEpi()).isEqualByComparingTo("78");
        assertThat(spec.getPpi()).isEqualByComparingTo("48");

        assertThat(spec.getConstruction()).isEqualTo("34X20/78X48");
        assertThat(spec.getDeclaredConstruction()).isEqualTo("34X20/76X49");
        // The buyer-facing wording, not the resultant blend - as the legacy form filled it.
        assertThat(spec.getComposition()).isEqualTo("55%Linen+45% BCI Cotton");
        assertThat(spec.getDeclaredComposition()).isEqualTo("55%Linen+45% BCI Cotton");
        assertThat(spec.getWarpYarnName()).isEqualTo("34 CWC 100% Cotton");
        assertThat(spec.getWeftYarnName()).isEqualTo("20 KW 100% Linen");

        assertThat(spec.getWeaveType()).isEqualTo("1/1");
        assertThat(spec.getWeaveStyle()).isEqualTo("Plain");
        assertThat(spec.getFinishWidth()).isEqualByComparingTo("54");
        assertThat(spec.getCuttableWidth()).isEqualByComparingTo("52");
        assertThat(spec.getShrinkageMechanical()).isEqualTo("15");
        assertThat(spec.getGsm()).isEqualByComparingTo("110");          // 109.84 shown whole
        assertThat(spec.getLcTenure()).isEqualTo("90 Days");

        assertThat(spec.getQuotedPrice()).isEqualByComparingTo("3.95");  // piMarkup, not piQuotPrice
        assertThat(spec.getBreakEvenPrice()).isEqualByComparingTo("3.8970712234057");
    }

    @Test
    void readsTheColourPlanTheBookingQuantitiesCameFrom() throws Exception {
        List<FabricCost.ColourQty> colours = translator.colours(load("18102503583"));

        assertThat(colours).extracting(FabricCost.ColourQty::colour)
            .containsExactly("Yellow AOP", "Black AOP", "Green AOP", "Alec Red", "Alec Blue");
        assertThat(colours).extracting(FabricCost.ColourQty::qty)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("10500"), new BigDecimal("5230"), new BigDecimal("2920"),
                             new BigDecimal("4000"), new BigDecimal("3300"));
    }

    @Test
    void translatesDobbyIdsAsTheLegacyBookingRecordedThem() throws Exception {
        FabricSpec spec = translator.toSpec(load("17072502459"));

        assertThat(spec.getWeaveType()).isEqualTo("Dobby : Medium Float");
        assertThat(spec.getWeaveStyle()).isEqualTo("Medium Float");
        assertThat(spec.getComposition()).isEqualTo("100% BCI Cotton");
        assertThat(spec.getQuotedPrice()).isEqualByComparingTo("1.7");
    }

    @Test
    void stampOverwritesCostingFiguresButKeepsWhatTheUserTyped() throws Exception {
        FabricSpec spec = new FabricSpec();
        spec.setComposition("Buyer's own wording");
        spec.setGsm(new BigDecimal("999"));
        spec.setQuotedPrice(new BigDecimal("0.01"));

        translator.stamp(load("18102503583"), spec);

        assertThat(spec.getComposition()).isEqualTo("Buyer's own wording");
        assertThat(spec.getGsm()).isEqualByComparingTo("110");
        assertThat(spec.getQuotedPrice()).isEqualByComparingTo("3.95");
        assertThat(spec.getConstruction()).isEqualTo("34X20/78X48");     // blank, so filled
    }

    @Test
    void anUnknownIdTranslatesToNothingRatherThanAGuess() {
        assertThat(CostingCatalog.weaveType("99")).isNull();
        assertThat(CostingCatalog.weaveType("x")).isNull();
        assertThat(CostingCatalog.lcTenure("1")).isEqualTo("At Sight");
    }
}
