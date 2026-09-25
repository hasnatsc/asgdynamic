package com.asg.fabricerp.global.numbering;

import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.OrgContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * The one place a business number comes from - documents, parties and their customer / supplier /
 * employee codes, items and the other masters, and vouchers and QC once those modules exist.
 * Replaces {@code DocumentNumberService}, whose atomic counter it keeps; what it adds is the
 * per-organization {@link NumberingScheme} (prefix, pattern, width, reset, branch scope), the
 * financial-year dimension and the issued-number ledger. Default shape: {@code BK-2026-000001}.
 *
 * <p>Pass the <b>document date</b> where there is one: a voucher dated 30 June belongs to the
 * financial year that ends that day, even if it is keyed in on 2 July.
 *
 * <p>Numbers are never reused. Cancelling or deleting a record leaves its number burnt, and so
 * does a save that fails after the number was taken - see {@link NumberAllocator}.
 */
@Service
public class BusinessNumberService {

    private final NumberAllocator allocator;
    private final OrgContext context;

    public BusinessNumberService(NumberAllocator allocator, OrgContext context) {
        this.allocator = allocator;
        this.context = context;
    }

    /** For masters: dated today, drawn for the caller's business unit. */
    public String next(NumberSeries series) {
        return next(series, LocalDate.now());
    }

    public String next(NumberSeries series, LocalDate date) {
        return allocate(series, date, context.businessUnitId(), context.businessUnitCode());
    }

    /** For documents: numbered for the unit that owns the document, which a revision inherits. */
    public String next(NumberSeries series, LocalDate date, BusinessUnit unit) {
        return unit == null
            ? next(series, date)
            : allocate(series, date, unit.getId(), unit.getCode());
    }

    /**
     * Claims a number typed in by hand so the generator skips it. Must run inside the save it
     * belongs to; see {@link NumberAllocator#reserve}.
     *
     * @throws IllegalArgumentException if it was already issued or reserved
     */
    public void reserve(NumberSeries series, String number) {
        if (!allocator.reserve(context.requireOrganizationId(), series, number, context.businessUnitId(), context.username())) {
            throw new IllegalArgumentException(
                "'%s' has already been issued. Numbers are never reused, even when their record was cancelled."
                    .formatted(number));
        }
    }

    private String allocate(NumberSeries series, LocalDate date, Long unitId, String unitCode) {
        if (date == null) throw new IllegalArgumentException("A number needs a date to find its financial year");
        return allocator.allocate(new NumberAllocator.Request(
            context.requireOrganizationId(), series, date, unitId, unitCode, context.username()));
    }
}
