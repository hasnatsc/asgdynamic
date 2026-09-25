package com.asg.fabricerp.global.numbering;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.Organization;
import com.asg.fabricerp.common.OrganizationRepository;
import com.asg.fabricerp.global.documents.DocumentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

/**
 * The numbering setup screen: what each series will issue next, and changing how it is numbered.
 * Reconfiguring is always safe for numbers already out - counters never go down and the ledger
 * rejects a repeat - so the only rules enforced here are the ones that keep <em>future</em>
 * numbers unique: see {@link NumberPattern#validate}.
 */
@Service
public class NumberingSetupService {

    public record SchemeRequest(String prefix, String pattern, Integer sequenceWidth, ResetPolicy resetPolicy,
                                CounterScope counterScope, Long version) { }

    @PersistenceContext
    private EntityManager em;

    private final NumberingSchemeRepository schemes;
    private final OrganizationRepository organizations;
    private final OrgContext context;

    public NumberingSetupService(NumberingSchemeRepository schemes, OrganizationRepository organizations,
                                 OrgContext context) {
        this.schemes = schemes;
        this.organizations = organizations;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Long orgId = context.requireOrganizationId();
        Organization org = organization(orgId);
        Map<String, NumberingScheme> stored = new HashMap<>();
        schemes.findByOrganizationId(orgId).forEach(s -> stored.put(s.getSeriesCode(), s));
        Set<String> prefixesInUse = new HashSet<>();
        stored.values().forEach(s -> prefixesInUse.add(s.getPrefix()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (NumberSeries series : NumberSeries.known()) {
            NumberingScheme scheme = stored.get(series.seriesCode());
            rows.add(row(orgId, org.getFiscalYearStartMonth(), series, scheme,
                scheme == null && prefixesInUse.contains(series.defaultPrefix())));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fiscalYear", fiscalYear(org));
        result.put("series", rows);
        return result;
    }

    @Transactional
    public Map<String, Object> update(String seriesCode, SchemeRequest r) {
        Long orgId = context.requireOrganizationId();
        NumberSeries series = NumberSeries.of(seriesCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown series: " + seriesCode));

        // Every series gets its row first, so the unique prefix constraint compares against all of
        // them - including ones never numbered yet - not just the ones someone already edited.
        String user = context.username();
        NumberSeries.known().forEach(s -> schemes.insertDefaults(orgId, s, user));

        NumberingScheme scheme = schemes.findByOrganizationIdAndSeriesCode(orgId, seriesCode).orElseThrow(() ->
            new IllegalStateException("%s's default prefix is taken by another series; free it first.".formatted(series.label())));
        if (r.version() != null && !r.version().equals(scheme.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(NumberingScheme.class, scheme.getId());
        }

        String prefix = r.prefix() == null ? null : r.prefix().trim().toUpperCase(Locale.ROOT);
        String pattern = r.pattern() == null ? null : r.pattern().trim().toUpperCase(Locale.ROOT);
        int width = r.sequenceWidth() == null ? series.defaultWidth() : r.sequenceWidth();
        ResetPolicy reset = Objects.requireNonNullElse(r.resetPolicy(), ResetPolicy.FINANCIAL_YEAR);
        CounterScope scope = Objects.requireNonNullElse(r.counterScope(), CounterScope.ORGANIZATION);
        NumberPattern.validate(prefix, pattern, width, reset, scope);

        schemes.findByOrganizationIdAndPrefix(orgId, prefix)
            .filter(other -> !other.getSeriesCode().equals(seriesCode))
            .ifPresent(other -> {
                throw new IllegalArgumentException("Prefix %s is already used by %s.".formatted(prefix,
                    NumberSeries.of(other.getSeriesCode()).map(NumberSeries::label).orElse(other.getSeriesCode())));
            });
        scheme.configure(prefix, pattern, width, reset, scope);

        // Longest it can render: a four-letter unit code and a full-width sequence.
        String longest = NumberPattern.render(pattern, new NumberPattern.Values(prefix, "XXXX",
            new FinancialYear(9999, 1), LocalDate.of(9999, 12, 31), 0, width));
        if (longest.length() > series.maxLength()) {
            throw new IllegalArgumentException("Numbers like %s are longer than the %d characters a %s code holds."
                .formatted(longest, series.maxLength(), series.label().toLowerCase(Locale.ROOT)));
        }

        schemes.saveAndFlush(scheme);
        Organization org = organization(orgId);
        return row(orgId, org.getFiscalYearStartMonth(), series, scheme, false);
    }

    @Transactional
    public Map<String, Object> updateFiscalYear(int startMonth) {
        Organization org = organization(context.requireOrganizationId());
        org.setFiscalYearStartMonth(startMonth);
        return fiscalYear(organizations.saveAndFlush(org));
    }

    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> row(Long orgId, int fyStartMonth, NumberSeries series, NumberingScheme scheme,
                                    boolean prefixClash) {
        String prefix = scheme == null ? series.defaultPrefix() : scheme.getPrefix();
        String pattern = scheme == null ? NumberPattern.DEFAULT : scheme.getPattern();
        int width = scheme == null ? series.defaultWidth() : scheme.getSequenceWidth();
        ResetPolicy reset = scheme == null ? ResetPolicy.FINANCIAL_YEAR : scheme.getResetPolicy();
        CounterScope scope = scheme == null ? CounterScope.ORGANIZATION : scheme.getCounterScope();

        LocalDate today = LocalDate.now();
        String period = reset.periodKey(today, fyStartMonth);
        Long unitKey = scope == CounterScope.BRANCH ? context.businessUnitId() : Long.valueOf(0L);
        long issued = unitKey == null ? 0 : lastValue(orgId, series.seriesCode(), unitKey, period);
        String unitCode = context.businessUnitCode();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seriesCode", series.seriesCode());
        row.put("label", series.label());
        row.put("group", group(series));
        row.put("prefix", prefix);
        row.put("pattern", pattern);
        row.put("sequenceWidth", width);
        row.put("resetPolicy", reset);
        row.put("counterScope", scope);
        row.put("maxLength", series.maxLength());
        row.put("configured", scheme != null);
        row.put("version", scheme == null ? null : scheme.getVersion());
        row.put("prefixClash", prefixClash);
        row.put("issuedThisPeriod", issued);
        row.put("nextNumber", NumberPattern.usesBranch(pattern) && unitCode == null ? null
            : NumberPattern.render(pattern, new NumberPattern.Values(prefix, unitCode,
                FinancialYear.containing(today, fyStartMonth), today, issued + 1, width)));
        return row;
    }

    private long lastValue(Long orgId, String seriesCode, Long unitKey, String period) {
        List<?> found = em.createNativeQuery("""
                SELECT last_value FROM gbl_number_counters
                WHERE organization_id = :orgId AND series_code = :series AND business_unit_id = :unit AND period_key = :period
                """)
            .setParameter("orgId", orgId)
            .setParameter("series", seriesCode)
            .setParameter("unit", unitKey)
            .setParameter("period", period)
            .getResultList();
        return found.isEmpty() ? 0 : ((Number) found.getFirst()).longValue();
    }

    private static Map<String, Object> fiscalYear(Organization org) {
        FinancialYear current = FinancialYear.containing(LocalDate.now(), org.getFiscalYearStartMonth());
        Map<String, Object> fy = new LinkedHashMap<>();
        fy.put("startMonth", org.getFiscalYearStartMonth());
        fy.put("startMonthName", Month.of(org.getFiscalYearStartMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        fy.put("label", current.label());
        fy.put("start", current.start());
        fy.put("end", current.end());
        return fy;
    }

    private static String group(NumberSeries series) {
        if (series instanceof DocumentType type) {
            String family = type.family().name();
            return family.charAt(0) + family.substring(1).toLowerCase(Locale.ROOT);
        }
        return switch ((BusinessSeries) series) {
            case PARTY, CUSTOMER, SUPPLIER, EMPLOYEE -> "Parties";
            case ITEM, ITEM_BRAND, ITEM_MODEL, YARN_TYPE, YARN_COUNT, YARN_PLY, YARN_BLEND -> "Items";
            case CONSTRUCTION -> "Fabric";
            case QC_INSPECTION -> "Quality";
            case VOUCHER, JOURNAL_VOUCHER, PAYMENT_VOUCHER, RECEIPT_VOUCHER, CONTRA_VOUCHER, SALES_VOUCHER,
                 PURCHASE_VOUCHER, PRODUCTION_VOUCHER -> "Accounts";
        };
    }

    private Organization organization(Long orgId) {
        return organizations.findById(orgId)
            .orElseThrow(() -> new IllegalStateException("Organization " + orgId + " not found"));
    }
}
