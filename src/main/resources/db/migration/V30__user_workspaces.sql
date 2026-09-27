-- Working across organizations, business units, stores and cost centres.
--
-- Two halves, deliberately kept apart:
--   * what a user MAY work in - granted by an administrator, as data-scope rows on two new
--     dimensions (ORGANIZATION, COST_CENTRE) beside V12's three;
--   * what a user CHOOSES to work in by default - their own pick among those, one row each in
--     sec_fabric_user_workspaces. Choosing never widens anything: the application re-checks the
--     row against the grants on every request and ignores whatever is no longer permitted.
--
-- sec_fabric_users.organization_id / business_unit_id / warehouse_id stay what an administrator
-- set: the user's home, used until they pick a default of their own and as the fallback after.
--
-- APPLY THIS BY HAND — same convention as every other migration here.

-- ---------------------------------------------------------------------------------------------
-- Scope dimensions
-- ---------------------------------------------------------------------------------------------

ALTER TABLE sec_fabric_data_scopes DROP CONSTRAINT ck_fab_scope_dimension;
ALTER TABLE sec_fabric_data_scopes ADD CONSTRAINT ck_fab_scope_dimension
    CHECK (dimension IN ('ORGANIZATION', 'BUSINESS_UNIT', 'WAREHOUSE', 'COST_CENTRE', 'MARKETING_TEAM'));

-- ---------------------------------------------------------------------------------------------
-- A user's chosen default workspace
-- ---------------------------------------------------------------------------------------------

CREATE TABLE sec_fabric_user_workspaces (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    organization_id   BIGINT       NOT NULL,
    business_unit_id  BIGINT       NOT NULL,
    warehouse_id      BIGINT,
    cost_centre_id    BIGINT,
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,

    -- One default per user; the unique constraint's index also serves the FK below.
    CONSTRAINT uk_fab_workspace_user UNIQUE (user_id),
    CONSTRAINT fk_fab_workspace_user FOREIGN KEY (user_id)
        REFERENCES sec_fabric_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_fab_workspace_org FOREIGN KEY (organization_id) REFERENCES org_organizations (id),
    CONSTRAINT fk_fab_workspace_unit FOREIGN KEY (business_unit_id) REFERENCES org_business_units (id),
    CONSTRAINT fk_fab_workspace_store FOREIGN KEY (warehouse_id) REFERENCES org_warehouses (id),
    CONSTRAINT fk_fab_workspace_centre FOREIGN KEY (cost_centre_id) REFERENCES acc_cost_centres (id)
);

CREATE INDEX ix_fab_workspace_org    ON sec_fabric_user_workspaces (organization_id);
CREATE INDEX ix_fab_workspace_unit   ON sec_fabric_user_workspaces (business_unit_id);
CREATE INDEX ix_fab_workspace_store  ON sec_fabric_user_workspaces (warehouse_id);
CREATE INDEX ix_fab_workspace_centre ON sec_fabric_user_workspaces (cost_centre_id);
