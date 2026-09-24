-- The rest of asfl-erp's admin module, ported onto the Role/Screen/Verb model V8 already
-- brought across: lockout and password state on the login (ADM-10), the access log (ADM-11),
-- and data scope — which rows a user may see, as distinct from which screens (ADM-3, ADM-4).
--
-- APPLY THIS BY HAND — same convention as every other migration here.

-- ---------------------------------------------------------------------------------------------
-- Optimistic-lock versions on SQL-seeded rows
-- ---------------------------------------------------------------------------------------------

-- Every seed INSERT before this one left `version` NULL, and Hibernate cannot update an entity
-- whose @Version is null — it fails at commit. So none of V10's 37 logins could be unlocked,
-- edited or given a password through the admin screen, and DevUserSeeder failed on the
-- V7/V11 roles. Zero is what Hibernate itself writes on insert.
UPDATE sec_fabric_users              SET version = 0 WHERE version IS NULL;
UPDATE sec_fabric_roles              SET version = 0 WHERE version IS NULL;
UPDATE sec_fabric_role_screen_grants SET version = 0 WHERE version IS NULL;
UPDATE org_organizations             SET version = 0 WHERE version IS NULL;
UPDATE org_business_units            SET version = 0 WHERE version IS NULL;
UPDATE org_warehouses                SET version = 0 WHERE version IS NULL;
UPDATE fab_attributes                SET version = 0 WHERE version IS NULL;

-- ---------------------------------------------------------------------------------------------
-- Login state
-- ---------------------------------------------------------------------------------------------

ALTER TABLE sec_fabric_users
    -- ADM-4: outside every scope filter. A flag on the user rather than a scope row, so that
    -- "sees everything" cannot be granted by accident and cannot be confused with a user
    -- nobody has configured yet (no scope rows, restricted: sees nothing, cannot log in).
    ADD COLUMN unrestricted          BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_login_count    INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN last_failed_login_at  TIMESTAMP,
    ADD COLUMN last_login_at         TIMESTAMP,
    ADD COLUMN locked_at             TIMESTAMP,
    ADD COLUMN locked_reason         VARCHAR(255),
    ADD COLUMN password_changed_at   TIMESTAMP,
    -- Stamped whenever an administrator sets the password: whoever typed it knows it, so it
    -- must stop being valid at the owner's first login.
    ADD COLUMN must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE;

-- Every existing login keeps exactly the access it had: before this migration nothing was
-- scoped, so the only faithful translation of an existing account is unrestricted. New
-- accounts default to restricted and must be given a scope before they can log in.
UPDATE sec_fabric_users SET unrestricted = TRUE;

UPDATE sec_fabric_users SET locked_at = now(), locked_reason = 'Locked before V12'
WHERE account_locked = TRUE;

-- ---------------------------------------------------------------------------------------------
-- Marketing teams — the dimension ADM-4 exists for
-- ---------------------------------------------------------------------------------------------

CREATE TABLE org_marketing_teams (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(150) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,

    CONSTRAINT uk_marketing_team_org_code UNIQUE (organization_id, code)
);

CREATE INDEX ix_marketing_team_org ON org_marketing_teams (organization_id);

-- Seed: the seven team-shaped entries of the legacy marketingGroup list
-- (business-logic-capture/master_data_catalog.json), code = the legacy id. The other five
-- entries in that list (Planning, Import - Commercial, Commercial PI/LC/CI - 1) are
-- departmental approval groups, not marketing teams, and are deliberately not seeded.
INSERT INTO org_marketing_teams (organization_id, code, name, active, version, created_by, created_at)
SELECT o.id, t.code, t.name, TRUE, 0, 'seed', now()
FROM org_organizations o,
     (VALUES ('1', 'Barcelona'), ('2', 'Las Vegas'), ('3', 'London'), ('4', 'Munich'),
             ('5', 'San Fransisco'), ('6', 'Sydney'), ('7', 'Tokyo')) AS t(code, name)
WHERE o.code = 'ASG'
  AND NOT EXISTS (SELECT 1 FROM org_marketing_teams m
                  WHERE m.organization_id = o.id AND m.code = t.code);

-- ADM-7: the team a document was raised under. Stamped once at creation (a Booking from its
-- creator's team, every downstream document from its parent) and never rewritten when the
-- person later moves team. NULL on every document raised before this migration.
ALTER TABLE gbl_business_documents ADD COLUMN marketing_team_id BIGINT;

CREATE INDEX ix_gbd_marketing_team ON gbl_business_documents (marketing_team_id);

-- ---------------------------------------------------------------------------------------------
-- Data scope — ADM-3
-- ---------------------------------------------------------------------------------------------

CREATE TABLE sec_fabric_data_scopes (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    dimension       VARCHAR(30)  NOT NULL,
    scope_value_id  BIGINT       NOT NULL,
    granted_from    DATE         NOT NULL,
    -- Effective-dated, never deleted: "what could this person see last March" keeps its answer.
    revoked_from    DATE,
    revoked_reason  VARCHAR(255),
    remarks         VARCHAR(255),
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,

    CONSTRAINT fk_fab_scope_user FOREIGN KEY (user_id) REFERENCES sec_fabric_users (id),
    CONSTRAINT ck_fab_scope_dimension
        CHECK (dimension IN ('BUSINESS_UNIT', 'WAREHOUSE', 'MARKETING_TEAM')),
    CONSTRAINT ck_fab_scope_dates CHECK (revoked_from IS NULL OR revoked_from >= granted_from)
);

CREATE INDEX ix_fab_scope_user ON sec_fabric_data_scopes (user_id);

-- ADM-4: at most one open marketing-team grant per user. The service checks this first so the
-- administrator reads a sentence; this index is what makes it true under concurrency.
CREATE UNIQUE INDEX uk_fab_scope_one_team ON sec_fabric_data_scopes (user_id)
    WHERE dimension = 'MARKETING_TEAM' AND revoked_from IS NULL;

-- ---------------------------------------------------------------------------------------------
-- Access log — ADM-11
-- ---------------------------------------------------------------------------------------------

-- Separate from the audit columns on every entity: a refused attempt changed no row, so there
-- is no row for an audit column to be on. Append-only; nothing updates or deletes here.
CREATE TABLE sec_access_log (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT,
    -- Text, not a foreign key: an unknown username is exactly what a failed login often is.
    username        VARCHAR(80),
    event           VARCHAR(30)  NOT NULL,
    target          VARCHAR(255),
    detail          VARCHAR(500),
    source_address  VARCHAR(64),
    occurred_at     TIMESTAMP    NOT NULL
);

CREATE INDEX ix_access_log_user ON sec_access_log (user_id, occurred_at);
CREATE INDEX ix_access_log_time ON sec_access_log (occurred_at);
