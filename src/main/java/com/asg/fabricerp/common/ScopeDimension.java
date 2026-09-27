package com.asg.fabricerp.common;

/**
 * The dimensions a user's access is granted along — ported from asfl-erp's admin module
 * (ADM-3). Independent of one another: restricting someone to one marketing team says
 * nothing about which warehouses they may see.
 *
 * <p>{@link #ORGANIZATION} is the odd one out: it is not a row filter inside a tenant but the
 * list of tenants a user may switch into, on top of the one their login belongs to. It is
 * therefore never part of a {@link RowScope}, and it applies to unrestricted users too —
 * "sees every row" means every row of an organization they may enter, not of every organization.
 */
public enum ScopeDimension {
    /** Another organization the user may work in. See the class comment. */
    ORGANIZATION,
    BUSINESS_UNIT,
    /** A store. asgdynamic's {@code ROLE_WEAVING_STORE}/{@code ROLE_PROCESSING_STORE} were this, modelled as roles. */
    WAREHOUSE,
    /** Which cost centres a user may pick and post to. */
    COST_CENTRE,
    /** ADM-4: the dimension the marketing teams depend on. */
    MARKETING_TEAM;

    /** Whether grants on this dimension narrow rows, i.e. belong in a {@link RowScope}. */
    public boolean narrowsRows() {
        return this != ORGANIZATION;
    }
}
