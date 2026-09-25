package com.asg.fabricerp.global.numbering;

import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link BusinessNumberService} against real PostgreSQL: the whole app boots, Flyway applies every
 * migration, Hibernate validates the entities against them, and numbers are drawn concurrently.
 *
 * <p>Opt-in, because it needs a database it may wipe. Point {@code FABRICERP_IT_DB_URL} at a
 * <b>throwaway</b> database - never the one the app uses; the numbering tables are emptied before
 * each test. Credentials come from {@code FABRICERP_DB_USER} / {@code FABRICERP_DB_PASSWORD} as usual.
 * <pre>
 *   createdb fabricerp_numbering_it
 *   FABRICERP_IT_DB_URL=jdbc:postgresql://localhost:5432/fabricerp_numbering_it mvn -Dtest=BusinessNumberingDatabaseIT test
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FABRICERP_IT_DB_URL", matches = "jdbc:postgresql:.+")
class BusinessNumberingDatabaseIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FABRICERP_IT_DB_URL"));
    }

    @Autowired private BusinessNumberService numbering;
    @Autowired private NumberingSetupService setup;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;

    @MockitoBean private OrgContext context;

    private long orgId;
    private BusinessUnit weaving;
    private BusinessUnit dyeing;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM gbl_issued_numbers");
        jdbc.update("DELETE FROM gbl_number_counters");
        jdbc.update("DELETE FROM gbl_numbering_schemes");
        orgId = jdbc.queryForObject("SELECT id FROM org_organizations WHERE code = 'ASG'", Long.class);
        jdbc.update("UPDATE org_organizations SET fiscal_year_start_month = 1 WHERE id = ?", orgId);
        jdbc.update("""
            INSERT INTO org_business_units (organization_id, code, name, active, version, created_by, created_at)
            SELECT ?, 'DY', 'Dyeing', TRUE, 0, 'it', now()
            WHERE NOT EXISTS (SELECT 1 FROM org_business_units WHERE organization_id = ? AND code = 'DY')
            """, orgId, orgId);

        weaving = unit("AF");
        dyeing = unit("DY");
        when(context.organizationId()).thenReturn(orgId);
        when(context.requireOrganizationId()).thenReturn(orgId);
        when(context.businessUnitId()).thenReturn(weaving.getId());
        when(context.businessUnitCode()).thenReturn("AF");
        when(context.username()).thenReturn("it");
        when(context.rowScope()).thenReturn(RowScope.unrestrictedScope());
    }

    private BusinessUnit unit(String code) {
        BusinessUnit u = new BusinessUnit(code, code);
        u.setId(jdbc.queryForObject("SELECT id FROM org_business_units WHERE organization_id = ? AND code = ?",
            Long.class, orgId, code));
        return u;
    }

    private static long seq(String number) {
        return Long.parseLong(number.substring(number.lastIndexOf('-') + 1));
    }

    @Test
    void theDefaultShapeIsPrefixYearSequence_andTheSchemeIsCreatedOnFirstUse() {
        LocalDate day = LocalDate.of(2026, 9, 25);
        assertThat(numbering.next(BusinessSeries.EMPLOYEE, day)).isEqualTo("EMP-2026-000001");
        assertThat(numbering.next(BusinessSeries.CUSTOMER, day)).isEqualTo("CUS-2026-000001");
        assertThat(numbering.next(BusinessSeries.VOUCHER, day)).isEqualTo("VCH-2026-000001");
        assertThat(numbering.next(BusinessSeries.VOUCHER, day)).isEqualTo("VCH-2026-000002");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gbl_numbering_schemes WHERE organization_id = ?",
            Integer.class, orgId)).isEqualTo(3);
    }

    @Test
    void concurrentCallers_includingOnTheFirstUseOfASeries_neverShareANumber() throws Exception {
        int threads = 16, perThread = 50;
        LocalDate day = LocalDate.of(2026, 9, 25);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<String>>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();   // all threads hit the not-yet-existing scheme and counter rows together
                List<String> mine = new ArrayList<>();
                for (int i = 0; i < perThread; i++) mine.add(numbering.next(BusinessSeries.QC_INSPECTION, day));
                return mine;
            }));
        }
        start.countDown();
        List<String> all = new ArrayList<>();
        for (Future<List<String>> f : futures) all.addAll(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(all).hasSize(threads * perThread).doesNotHaveDuplicates()
            .allSatisfy(n -> assertThat(n).startsWith("QC-2026-"));
        // No gaps either: every value 1..800 was handed out exactly once.
        assertThat(all.stream().map(BusinessNumberingDatabaseIT::seq).sorted().toList())
            .isEqualTo(java.util.stream.LongStream.rangeClosed(1, threads * perThread).boxed().toList());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gbl_issued_numbers WHERE series_code = 'QC_INSPECTION'",
            Integer.class)).isEqualTo(threads * perThread);
    }

    @Test
    void theCounterRestartsWithTheFinancialYearTheDocumentDateFallsIn() {
        setup.updateFiscalYear(7);   // July-June

        assertThat(numbering.next(BusinessSeries.VOUCHER, LocalDate.of(2026, 6, 29))).isEqualTo("VCH-2025-000001");
        assertThat(numbering.next(BusinessSeries.VOUCHER, LocalDate.of(2026, 7, 1))).isEqualTo("VCH-2026-000001");
        // Back-dated into the previous year after the new one started: continues that year's counter.
        assertThat(numbering.next(BusinessSeries.VOUCHER, LocalDate.of(2026, 6, 30))).isEqualTo("VCH-2025-000002");
        assertThat(numbering.next(BusinessSeries.VOUCHER, LocalDate.of(2027, 6, 30))).isEqualTo("VCH-2026-000002");
    }

    @Test
    void aPerBranchSeriesCountsEachBranchSeparately() {
        setup.update("BOOKING", new NumberingSetupService.SchemeRequest(
            "BK", "{PREFIX}-{BRANCH}-{FY}-{SEQ}", 5, ResetPolicy.FINANCIAL_YEAR, CounterScope.BRANCH, null));
        LocalDate day = LocalDate.of(2026, 9, 25);

        assertThat(numbering.next(com.asg.fabricerp.global.documents.DocumentType.BOOKING, day, weaving)).isEqualTo("BK-AF-2026-00001");
        assertThat(numbering.next(com.asg.fabricerp.global.documents.DocumentType.BOOKING, day, dyeing)).isEqualTo("BK-DY-2026-00001");
        assertThat(numbering.next(com.asg.fabricerp.global.documents.DocumentType.BOOKING, day, weaving)).isEqualTo("BK-AF-2026-00002");
    }

    @Test
    void aNumberTakenByASaveThatFailedIsNeverIssuedAgain() {
        LocalDate day = LocalDate.of(2026, 9, 25);
        String[] burnt = new String[1];
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            burnt[0] = numbering.next(BusinessSeries.SUPPLIER, day);
            throw new IllegalStateException("the supplier save failed");
        })).hasMessageContaining("save failed");

        assertThat(burnt[0]).isEqualTo("SUP-2026-000001");
        assertThat(numbering.next(BusinessSeries.SUPPLIER, day)).isEqualTo("SUP-2026-000002");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gbl_issued_numbers WHERE number = 'SUP-2026-000001'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void numbersAlreadyInTheLedgerAreSkipped_andCannotBeReservedTwice() {
        LocalDate day = LocalDate.of(2026, 9, 25);
        tx.executeWithoutResult(status -> numbering.reserve(BusinessSeries.PARTY, "PT-2026-000002"));

        assertThat(numbering.next(BusinessSeries.PARTY, day)).isEqualTo("PT-2026-000001");
        assertThat(numbering.next(BusinessSeries.PARTY, day)).isEqualTo("PT-2026-000003");   // 000002 was taken by hand
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> numbering.reserve(BusinessSeries.PARTY, "PT-2026-000003")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already been issued");
    }

    @Test
    void theDatabaseRefusesAConfigurationThatCouldRepeatANumber() {
        String insert = """
            INSERT INTO gbl_numbering_schemes (organization_id, series_code, prefix, pattern, sequence_width, reset_policy, counter_scope)
            VALUES (?, ?, ?, ?, 6, ?, ?)""";
        // Per-branch counters without the branch in the number.
        assertThatThrownBy(() -> jdbc.update(insert, orgId, "X1", "XA", "{PREFIX}-{FY}-{SEQ}", "FINANCIAL_YEAR", "BRANCH"))
            .hasMessageContaining("ck_gns_branch_in_pattern");
        // A yearly reset without the year in the number.
        assertThatThrownBy(() -> jdbc.update(insert, orgId, "X2", "XB", "{PREFIX}-{SEQ}", "FINANCIAL_YEAR", "ORGANIZATION"))
            .hasMessageContaining("ck_gns_fy_in_pattern");
        // Two series on one prefix.
        jdbc.update(insert, orgId, "X3", "XC", "{PREFIX}-{FY}-{SEQ}", "FINANCIAL_YEAR", "ORGANIZATION");
        assertThatThrownBy(() -> jdbc.update(insert, orgId, "X4", "XC", "{PREFIX}-{FY}-{SEQ}", "FINANCIAL_YEAR", "ORGANIZATION"))
            .hasMessageContaining("uk_gns_org_prefix");
        // And a number can be in the ledger only once.
        jdbc.update("INSERT INTO gbl_issued_numbers (organization_id, number, series_code) VALUES (?, 'DUP-1', 'X3')", orgId);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO gbl_issued_numbers (organization_id, number, series_code) VALUES (?, 'DUP-1', 'X3')", orgId))
            .hasMessageContaining("uk_gin_org_number");
    }

    @Test
    void theSetupScreenRefusesAPrefixAnotherSeriesUses() {
        assertThatThrownBy(() -> setup.update("VOUCHER", new NumberingSetupService.SchemeRequest(
                "BK", NumberPattern.DEFAULT, 6, ResetPolicy.FINANCIAL_YEAR, CounterScope.ORGANIZATION, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already used by Booking");
    }
}
