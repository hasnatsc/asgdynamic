package com.asg.fabricerp.common;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Stamps audit columns and tenancy on insert/update.
 *
 * <p>Spring Boot wires Hibernate's {@code SpringBeanContainer} by default, so an
 * {@code @EntityListeners} class can be a Spring bean with constructor injection. That is
 * what lets {@link OrgContext} be injected here instead of reached through static state.
 *
 * <p>{@link ObjectProvider} is used deliberately: the context is request-scoped, and some
 * writes legitimately happen outside a request (schedulers, bootstrap data loaders). In
 * those cases there is no caller, and the entity is stamped {@code system} rather than
 * blowing up mid-flush.
 */
@Component
public class OrgContextListener {

    private static final String SYSTEM_USER = "system";

    private final ObjectProvider<OrgContext> contextProvider;

    public OrgContextListener(ObjectProvider<OrgContext> contextProvider) {
        this.contextProvider = contextProvider;
    }

    @PrePersist
    void onPersist(Object entity) {
        if (!(entity instanceof AuditableEntity auditable)) return;
        LocalDateTime now = LocalDateTime.now();
        auditable.stampCreated(currentUser(), now);
        applyTenancy(entity);
    }

    @PreUpdate
    void onUpdate(Object entity) {
        if (!(entity instanceof AuditableEntity auditable)) return;
        auditable.stampUpdated(currentUser(), LocalDateTime.now());
    }

    /**
     * Tenancy is stamped from the context only when the entity has not already been given
     * one. An explicit assignment by a service always wins — data-migration and
     * cross-tenant admin jobs depend on that.
     */
    private void applyTenancy(Object entity) {
        if (!(entity instanceof OrgScoped scoped)) return;
        if (scoped.getOrganizationId() != null) return;
        OrgContext ctx = contextProvider.getIfAvailable();
        if (ctx != null && ctx.organizationId() != null) {
            scoped.setOrganizationId(ctx.organizationId());
        }
    }

    private String currentUser() {
        OrgContext ctx = contextProvider.getIfAvailable();
        String user = ctx == null ? null : ctx.username();
        return (user == null || user.isBlank()) ? SYSTEM_USER : user;
    }
}
