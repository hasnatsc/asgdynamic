package com.asg.fabricerp.common;

/**
 * The caller's operating scope: tenant, business unit, warehouse, user.
 *
 * <h2>Why this is an interface and not a static holder</h2>
 * SpindleERP exposes the same information through {@code ContextProvider} — a class of
 * {@code static} fields populated with six repositories at startup, called directly from
 * {@code @PrePersist}. That works at runtime but has three costs this design avoids:
 *
 * <ul>
 *   <li><b>Untestable entities.</b> Persisting an entity reaches into static state, so a
 *       unit test must boot enough of Spring to populate it. Here a test passes a stub.</li>
 *   <li><b>Repository calls inside a lifecycle callback.</b> {@code getOrganizationReference()}
 *       hits a repository from within {@code @PrePersist}, i.e. a query mid-flush. This
 *       interface returns ids; the listener resolves references once, deliberately.</li>
 *   <li><b>Hidden coupling.</b> Any class can silently depend on ambient tenancy. An injected
 *       dependency makes that visible in the constructor.</li>
 * </ul>
 *
 * Implementations must be request/session scoped and must never be cached in a singleton field.
 */
public interface OrgContext {

    Long organizationId();

    Long businessUnitId();

    /** Two-letter unit code embedded in every document number, e.g. "AF". */
    String businessUnitCode();

    Long warehouseId();

    String username();

    default Long requireOrganizationId() {
        Long id = organizationId();
        if (id == null) throw new IllegalStateException("No organization in context");
        return id;
    }

    default Long requireBusinessUnitId() {
        Long id = businessUnitId();
        if (id == null) throw new IllegalStateException("No business unit in context");
        return id;
    }

    default String requireBusinessUnitCode() {
        String code = businessUnitCode();
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("No business unit code in context; cannot number a document");
        }
        return code;
    }

    default Long requireWarehouseId() {
        Long id = warehouseId();
        if (id == null) throw new IllegalStateException("No warehouse in context");
        return id;
    }
}
