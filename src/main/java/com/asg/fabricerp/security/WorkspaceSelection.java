package com.asg.fabricerp.security;

import java.io.Serializable;

/**
 * Organization, business unit, store and cost centre as somebody asked for them - in the header
 * switcher for this session, or saved as their default. Only a request: {@link WorkspaceResolver}
 * checks it against what the user is granted before any of it is used.
 *
 * <p>Serializable because the session holds one.
 */
public record WorkspaceSelection(Long organizationId, Long businessUnitId, Long warehouseId, Long costCentreId)
        implements Serializable {

    static final String SESSION_ATTRIBUTE = WorkspaceSelection.class.getName();
}
