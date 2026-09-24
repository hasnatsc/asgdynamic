package com.asg.fabricerp.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Master/setup entities: tenant-scoped, soft-deletable, de-activatable.
 *
 * <p>Mirrors SpindleERP's {@code BaseOrgEntity} role, but inherits its audit columns from
 * {@link AuditableEntity} rather than redeclaring them.
 */
@MappedSuperclass
public abstract class BaseOrgEntity extends AuditableEntity implements OrgScoped {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    /**
     * Soft delete. Every repository query must filter this - see
     * {@code OrgScopedRepository}, which makes that the default rather than a convention
     * each query has to remember.
     */
    @Column(nullable = false)
    private Boolean deleted = Boolean.FALSE;

    @Override public Long getOrganizationId()              { return organizationId; }
    @Override public void setOrganizationId(Long id)       { this.organizationId = id; }
    public Boolean getActive()                             { return active; }
    public void setActive(Boolean active)                  { this.active = active; }
    public Boolean getDeleted()                            { return deleted; }

    public void markDeleted() { this.deleted = Boolean.TRUE; }
}
