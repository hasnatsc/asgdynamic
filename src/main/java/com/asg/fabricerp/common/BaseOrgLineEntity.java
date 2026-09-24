package com.asg.fabricerp.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Child rows of a document (lines, lots, terms).
 *
 * <p>No soft-delete flag: a line's lifetime is its parent's. Removing a line from the
 * parent collection deletes the row via {@code orphanRemoval}, which is what the legacy
 * "post the whole document at once" UI needs.
 */
@MappedSuperclass
public abstract class BaseOrgLineEntity extends AuditableEntity implements OrgScoped {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Override public Long getOrganizationId()        { return organizationId; }
    @Override public void setOrganizationId(Long id) { this.organizationId = id; }
}
