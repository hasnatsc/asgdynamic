package com.asg.fabricerp.common;

/**
 * The dimensions a user's row visibility is narrowed along — ported from asfl-erp's admin
 * module (ADM-3). Independent of one another: restricting someone to one marketing team says
 * nothing about which warehouses they may see.
 *
 * <p>asfl-erp also has {@code COST_CENTRE}. It is not here because nothing in this system
 * carries a cost centre yet, and a dimension no row can be filtered on would be a grant that
 * silently does nothing.
 */
public enum ScopeDimension {
    BUSINESS_UNIT,
    /** A store. asgdynamic's {@code ROLE_WEAVING_STORE}/{@code ROLE_PROCESSING_STORE} were this, modelled as roles. */
    WAREHOUSE,
    /** ADM-4: the dimension the marketing teams depend on. */
    MARKETING_TEAM
}
