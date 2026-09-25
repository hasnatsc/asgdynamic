package com.asg.fabricerp.global.numbering;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * The database half of {@link BusinessNumberService}: one allocation, in its own transaction.
 * Kept a separate bean so {@code REQUIRES_NEW} goes through a Spring proxy - it would be silently
 * ignored on a call from inside the same class.
 *
 * <h2>Why each step is shaped the way it is</h2>
 * <ul>
 *   <li><b>Counter: one atomic upsert.</b> {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}
 *       takes the row lock and increments in one statement, so two concurrent callers are
 *       serialized on the row and each gets its own value; the first use of a new period cannot
 *       race either. {@code SELECT MAX(code)+1} - what SpindleERP does - hands both the same.</li>
 *   <li><b>{@code REQUIRES_NEW}.</b> The counter row is locked only for this short transaction, not
 *       for the caller's whole save, and a save that rolls back does not roll the counter back: its
 *       number stays burnt. Gaps are acceptable; a number printed on a cancelled document coming
 *       back on a new one is not.</li>
 *   <li><b>Ledger.</b> Each number is also inserted into {@code gbl_issued_numbers}, unique per
 *       organization, in the same transaction. A number that is already there - issued by an
 *       earlier configuration, backfilled from before this service existed, or reserved by hand -
 *       is skipped and the next value taken.</li>
 * </ul>
 */
@Component
public class NumberAllocator {

    /** Consecutive taken numbers tolerated before concluding a counter needs seeding. */
    static final int MAX_SKIPS = 1000;

    private static final String BUMP_COUNTER_SQL = """
            INSERT INTO gbl_number_counters (organization_id, series_code, business_unit_id, period_key, last_value, updated_at)
            VALUES (:orgId, :series, :unitKey, :period, 1, now())
            ON CONFLICT (organization_id, series_code, business_unit_id, period_key)
            DO UPDATE SET last_value = gbl_number_counters.last_value + 1, updated_at = now()
            RETURNING last_value
            """;

    private static final String RECORD_SQL = """
            INSERT INTO gbl_issued_numbers
                (organization_id, number, series_code, business_unit_id, period_key, sequence_value, issued_by, issued_at)
            VALUES (:orgId, :number, :series, :unitId, :period, :value, :user, now())
            ON CONFLICT (organization_id, number) DO NOTHING
            RETURNING id
            """;

    public record Request(Long organizationId, NumberSeries series, LocalDate date,
                          Long businessUnitId, String businessUnitCode, String issuedBy) { }

    @PersistenceContext
    private EntityManager em;

    private final NumberingSchemeRepository schemes;

    public NumberAllocator(NumberingSchemeRepository schemes) {
        this.schemes = schemes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String allocate(Request r) {
        NumberingScheme scheme = schemeFor(r.organizationId(), r.series(), r.issuedBy());
        int fyStartMonth = fiscalYearStartMonth(r.organizationId());
        String period = scheme.getResetPolicy().periodKey(r.date(), fyStartMonth);
        FinancialYear fy = FinancialYear.containing(r.date(), fyStartMonth);

        long unitKey = 0L;   // 0 = the organization-wide counter
        if (scheme.getCounterScope() == CounterScope.BRANCH) {
            if (r.businessUnitId() == null) {
                throw new IllegalStateException("%s is numbered per branch, but no business unit is in context"
                    .formatted(r.series().label()));
            }
            unitKey = r.businessUnitId();
        }

        for (int attempt = 0; attempt < MAX_SKIPS; attempt++) {
            long value = ((Number) em.createNativeQuery(BUMP_COUNTER_SQL)
                .setParameter("orgId", r.organizationId())
                .setParameter("series", r.series().seriesCode())
                .setParameter("unitKey", unitKey)
                .setParameter("period", period)
                .getSingleResult()).longValue();

            String number = NumberPattern.render(scheme.getPattern(), new NumberPattern.Values(
                scheme.getPrefix(), r.businessUnitCode(), fy, r.date(), value, scheme.getSequenceWidth()));
            if (number.length() > r.series().maxLength()) {
                // Throwing rolls the increment back with it, so nothing is burnt.
                throw new IllegalStateException("%s would be numbered '%s', longer than the %d characters its code holds; shorten the pattern."
                    .formatted(r.series().label(), number, r.series().maxLength()));
            }
            if (record(r.organizationId(), number, r.series(), r.businessUnitId(), period, value, r.issuedBy())) {
                return number;
            }
        }
        throw new IllegalStateException(
            "The next %d %s numbers are all taken already. Raise its counter past the numbers in circulation."
                .formatted(MAX_SKIPS, r.series().label()));
    }

    /**
     * Records a number chosen by hand so the generator will never issue it. Runs in the caller's
     * transaction on purpose: if their save fails, the reservation goes with it and they can use
     * the code on the next attempt.
     *
     * @return false if the number was already issued or reserved
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserve(Long organizationId, NumberSeries series, String number, Long businessUnitId, String user) {
        return record(organizationId, number, series, businessUnitId, null, null, user);
    }

    private boolean record(Long orgId, String number, NumberSeries series, Long unitId, String period, Long value, String user) {
        return !em.createNativeQuery(RECORD_SQL)
            .unwrap(NativeQuery.class)
            .setParameter("orgId", orgId)
            .setParameter("number", number)
            .setParameter("series", series.seriesCode())
            .setParameter("unitId", unitId, Long.class)
            .setParameter("period", period, String.class)
            .setParameter("value", value, Long.class)
            .setParameter("user", user, String.class)
            .getResultList()
            .isEmpty();
    }

    private NumberingScheme schemeFor(Long orgId, NumberSeries series, String user) {
        return schemes.findByOrganizationIdAndSeriesCode(orgId, series.seriesCode()).orElseGet(() -> {
            schemes.insertDefaults(orgId, series, user);
            return schemes.findByOrganizationIdAndSeriesCode(orgId, series.seriesCode()).orElseThrow(() ->
                new IllegalStateException("Cannot number %s: its default prefix %s is already used by %s. Give it another prefix on the numbering setup screen."
                    .formatted(series.label(), series.defaultPrefix(),
                        schemes.findByOrganizationIdAndPrefix(orgId, series.defaultPrefix())
                            .map(NumberingScheme::getSeriesCode).orElse("another series"))));
        });
    }

    private int fiscalYearStartMonth(Long orgId) {
        return ((Number) em.createNativeQuery("SELECT fiscal_year_start_month FROM org_organizations WHERE id = :id")
            .setParameter("id", orgId)
            .getSingleResult()).intValue();
    }
}
