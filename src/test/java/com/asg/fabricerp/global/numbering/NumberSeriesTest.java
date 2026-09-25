package com.asg.fabricerp.global.numbering;

import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.party.PartyRoleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The defaults every organization starts from. A clash here would surface as the second series
 * refusing to number at all (the prefix is unique per organization), so it is caught at build time.
 */
class NumberSeriesTest {

    @Test
    void everySeriesHasItsOwnPrefixAndCode() {
        List<NumberSeries> all = NumberSeries.known();
        assertThat(all).extracting(NumberSeries::defaultPrefix).doesNotHaveDuplicates();
        assertThat(all).extracting(NumberSeries::seriesCode).doesNotHaveDuplicates()
            .allSatisfy(code -> assertThat(code.length()).isLessThanOrEqualTo(40));
    }

    @Test
    void everyDefaultIsAValidScheme_thatFitsItsColumnEvenPerBranch() {
        for (NumberSeries s : NumberSeries.known()) {
            assertThatCode(() -> NumberPattern.validate(s.defaultPrefix(), NumberPattern.DEFAULT, s.defaultWidth(),
                ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION)).as(s.seriesCode()).doesNotThrowAnyException();
            String perBranch = NumberPattern.render("{PREFIX}-{BRANCH}-{FY}-{SEQ}", new NumberPattern.Values(
                s.defaultPrefix(), "XXXX", new FinancialYear(2026, 1), LocalDate.of(2026, 1, 1), 1, s.defaultWidth()));
            assertThat(perBranch.length()).as(s.seriesCode()).isLessThanOrEqualTo(s.maxLength());
        }
    }

    @Test
    void theRequestedSeriesExist() {
        assertThat(NumberSeries.of("EMPLOYEE")).contains(BusinessSeries.EMPLOYEE);
        assertThat(NumberSeries.of("VOUCHER")).contains(BusinessSeries.VOUCHER);
        assertThat(NumberSeries.of("QC_INSPECTION")).contains(BusinessSeries.QC_INSPECTION);
        assertThat(NumberSeries.of("BOOKING")).contains(DocumentType.BOOKING);
        assertThat(BusinessSeries.VOUCHER.defaultPrefix()).isEqualTo("VCH");
        assertThat(BusinessSeries.CUSTOMER.defaultPrefix()).isEqualTo("CUS");
        assertThat(BusinessSeries.EMPLOYEE.defaultPrefix()).isEqualTo("EMP");
    }

    @Test
    void onlyTheCapacitiesACodeIsPrintedUnderAreNumbered() {
        assertThat(BusinessSeries.forRole(PartyRoleType.CUSTOMER)).contains(BusinessSeries.CUSTOMER);
        assertThat(BusinessSeries.forRole(PartyRoleType.SUPPLIER)).contains(BusinessSeries.SUPPLIER);
        assertThat(BusinessSeries.forRole(PartyRoleType.EMPLOYEE)).contains(BusinessSeries.EMPLOYEE);
        assertThat(BusinessSeries.forRole(PartyRoleType.BANK)).isEmpty();
    }
}
