package com.asg.fabricerp.security;

import com.asg.fabricerp.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The workspace a user chose as their default - their own setting, not an administrator's.
 *
 * <p>Holds ids only and grants nothing: {@link WorkspaceResolver} re-checks it against the user's
 * scope grants every time it is read, so a default that has since been revoked is passed over
 * rather than obeyed. Not an {@code OrgScoped} entity on purpose - it names an organization, it
 * does not belong to one, and the listener must not stamp the caller's over it.
 */
@Entity
@Table(name = "sec_fabric_user_workspaces")
public class UserWorkspace extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "business_unit_id", nullable = false)
    private Long businessUnitId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "cost_centre_id")
    private Long costCentreId;

    protected UserWorkspace() { }

    public UserWorkspace(Long userId) {
        this.userId = userId;
    }

    public void choose(Workspace workspace) {
        this.organizationId = workspace.organizationId();
        this.businessUnitId = workspace.businessUnitId();
        this.warehouseId = workspace.warehouseId();
        this.costCentreId = workspace.costCentreId();
    }

    public WorkspaceSelection selection() {
        return new WorkspaceSelection(organizationId, businessUnitId, warehouseId, costCentreId);
    }

    public Long getUserId() { return userId; }
}
