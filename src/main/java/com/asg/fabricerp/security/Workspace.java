package com.asg.fabricerp.security;

/**
 * Where the signed-in user is working right now: the organization every list is narrowed to and
 * every new record is stamped with, the business unit whose code numbers their documents, and the
 * store and cost centre new entries default to. Always a combination the user is permitted - see
 * {@link WorkspaceResolver}.
 *
 * <p>The code/name pairs are for the header only; they are null when the workspace was built
 * without looking anything up (a principal made straight from a {@link FabricUser}).
 */
public record Workspace(Long organizationId, String organizationCode, String organizationName,
                        Long businessUnitId, String businessUnitCode,
                        Long warehouseId,
                        Long costCentreId, String costCentreCode, String costCentreName) {

    /** The user's administrator-set home, taken as it stands. */
    static Workspace home(FabricUser user) {
        return new Workspace(user.getOrganizationId(), null, null,
            user.getBusinessUnitId(), user.getBusinessUnitCode(), user.getWarehouseId(), null, null, null);
    }

    public WorkspaceSelection selection() {
        return new WorkspaceSelection(organizationId, businessUnitId, warehouseId, costCentreId);
    }
}
