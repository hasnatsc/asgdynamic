package com.asg.fabricerp.commercial;

import com.asg.fabricerp.commercial.CommercialTerms.RealizationStep;
import com.asg.fabricerp.global.documents.BusinessDocumentColorLine;
import com.asg.fabricerp.global.documents.BusinessDocumentLineGroup;
import com.asg.fabricerp.global.documents.FabricSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The commercial rules that need no database: the step table, fabric weights, amounts in words, realization order. */
class CommercialRulesTest {

    @ParameterizedTest
    @EnumSource(CommercialStep.class)
    void eachStepsScreenAuthorityAndPathAgree(CommercialStep step) {
        assertThat(step.type().roleRoot()).isEqualTo(step.screen().name());
        assertThat(step.screen().path()).isEqualTo("/" + step.slug());
        assertThat(CommercialStep.ofSlug(step.slug())).isSameAs(step);
        assertThat(step.type().isRevisable()).isTrue();   // the family is; the CI screen declines it
    }

    @Test
    void anImportPiSharesARequisitionsOrderedBalance() {
        assertThat(CommercialStep.IPI.stream()).isEqualTo("PURCHASE_ORDER");
        assertThat(CommercialStep.ELC.stream()).isEqualTo("EXPORT_LETTER_OF_CREDIT");
        assertThat(CommercialStep.ECI.isRevisable()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"30, 30", "30/1, 30", "30/2, 15", "40D, 132.875", "30/1+40D, 30", "'', 0"})
    void countsReadAsTheLegacyScreenReadThem(String count, double ne) {
        assertThat(FabricWeights.count(count)).isCloseTo(ne, within(0.001));
    }

    @Test
    void aCountWithoutARatioIsWeighedAsRatioOne() {
        assertThat(FabricWeights.resultant(new String[] {"30", null, null}, new BigDecimal[] {null, null, null})).isCloseTo(30, within(1e-9));
        // Two warp counts 1:1 - resultant 2 / (1/20 + 1/40) = 26.67 Ne.
        assertThat(FabricWeights.resultant(new String[] {"20", "40", null}, new BigDecimal[] {BigDecimal.ONE, BigDecimal.ONE, null}))
            .isCloseTo(26.6667, within(0.001));
    }

    @Test
    void weightFollowsTheLegacyFormula() {
        BusinessDocumentLineGroup g = new BusinessDocumentLineGroup();
        FabricSpec f = g.getFabric();
        f.setWarpCount1("30");
        f.setWeftCount1("30");
        f.setEpi(new BigDecimal("120"));
        f.setPpi(new BigDecimal("80"));
        f.setFinishWidth(new BigDecimal("58"));
        BusinessDocumentColorLine l = new BusinessDocumentColorLine();
        l.setQuantity(new BigDecimal("1000"));
        g.addColorLine(l);
        FabricWeights.Result w = FabricWeights.of(List.of(g));
        // 1000 yd = 914.4 m; 58" = 1.4732 m; (120/30 + 80/30) × 0.024412 = 0.162747 → 219.23 kg net, ×1.03 gross.
        assertThat(w.net().doubleValue()).isCloseTo(219.23, within(0.05));
        assertThat(w.gross().doubleValue()).isCloseTo(219.23 * 1.03, within(0.05));
        assertThat(w.unweighed()).isEmpty();

        BusinessDocumentLineGroup blank = new BusinessDocumentLineGroup();
        blank.addColorLine(l);
        assertThat(FabricWeights.of(List.of(blank)).unweighed()).hasSize(1);
    }

    @Test
    void amountsAreWrittenOutAsTheBankReadsThem() {
        assertThat(AmountInWords.of(new BigDecimal("2980.50"), "USD")).isEqualTo("US DOLLAR TWO THOUSAND NINE HUNDRED EIGHTY AND CENTS FIFTY ONLY");
        assertThat(AmountInWords.of(new BigDecimal("1250000"), "USD")).isEqualTo("US DOLLAR ONE MILLION TWO HUNDRED FIFTY THOUSAND ONLY");
        assertThat(AmountInWords.of(new BigDecimal("12500000"), "BDT")).isEqualTo("TAKA ONE CRORE TWENTY FIVE LAKH ONLY");
    }

    @Test
    void realizationGoesInOrderAndPurchaseIsOptional() {
        assertThat(RealizationStep.BANK_SUBMISSION.required())
            .containsExactly(RealizationStep.DOC_SUBMISSION, RealizationStep.PARTY_ACCEPTANCE);
        assertThat(RealizationStep.FINAL_PAYMENT.required()).doesNotContain(RealizationStep.PURCHASE).contains(RealizationStep.BANK_MATURITY);
        assertThat(RealizationStep.PURCHASE.hasAmount()).isTrue();
    }
}
