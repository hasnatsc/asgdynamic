-- The approval engine - asfl-erp's core.approval (V2 matrix/level/request/action, V27 team-wise
-- routing), on asgdynamic's documents.
--
--   apr_matrices        who approves one document type in one business unit, optionally for one
--                       marketing team. The team's own matrix is preferred; the unit-wide one
--                       governs every other team and the unteamed documents.
--   apr_matrix_levels   the signatures, in order. Each names a ROLE or one USER, never both, and
--                       may apply only within an amount band.
--   apr_requests        one submission: which matrix it was submitted under (never re-resolved),
--                       how many levels its amount needs, where it has got to, who raised it.
--   apr_document_history  already the trail; now also says which request and which level.
--
-- Where a document type has no matrix at all, the engine keeps the rule this system had before:
-- one level, anyone holding the screen's APPROVE verb. A matrix narrows that; its absence is not
-- an auto-approval.

CREATE TABLE apr_matrices (
    id                BIGSERIAL    PRIMARY KEY,
    organization_id   BIGINT       NOT NULL,
    business_unit_id  BIGINT       NOT NULL,
    marketing_team_id BIGINT,
    document_type     VARCHAR(40)  NOT NULL,
    name              VARCHAR(150) NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,

    CONSTRAINT fk_apr_matrix_unit FOREIGN KEY (business_unit_id) REFERENCES org_business_units (id),
    CONSTRAINT fk_apr_matrix_team FOREIGN KEY (marketing_team_id) REFERENCES org_marketing_teams (id)
);

-- Two partial indexes, as asfl-erp V27: a plain unique over the team column would let any number
-- of unit-wide rows through, since NULL never equals NULL.
CREATE UNIQUE INDEX uq_apr_matrix_team ON apr_matrices (organization_id, business_unit_id, document_type, marketing_team_id)
    WHERE marketing_team_id IS NOT NULL AND deleted = FALSE;
CREATE UNIQUE INDEX uq_apr_matrix_wide ON apr_matrices (organization_id, business_unit_id, document_type)
    WHERE marketing_team_id IS NULL AND deleted = FALSE;

CREATE TABLE apr_matrix_levels (
    id          BIGSERIAL     PRIMARY KEY,
    matrix_id   BIGINT        NOT NULL,
    sequence    INTEGER       NOT NULL,
    role_id     BIGINT,
    user_id     BIGINT,
    min_amount  NUMERIC(18, 2),
    max_amount  NUMERIC(18, 2),
    version     BIGINT,
    created_by  VARCHAR(100),
    created_at  TIMESTAMP,
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMP,

    CONSTRAINT fk_apr_level_matrix FOREIGN KEY (matrix_id) REFERENCES apr_matrices (id) ON DELETE CASCADE,
    CONSTRAINT fk_apr_level_role FOREIGN KEY (role_id) REFERENCES sec_fabric_roles (id),
    CONSTRAINT fk_apr_level_user FOREIGN KEY (user_id) REFERENCES sec_fabric_users (id),
    CONSTRAINT ck_apr_level_role_or_user CHECK ((role_id IS NULL) <> (user_id IS NULL)),
    CONSTRAINT ck_apr_level_band CHECK (min_amount IS NULL OR max_amount IS NULL OR max_amount >= min_amount),
    CONSTRAINT uk_apr_level_sequence UNIQUE (matrix_id, sequence)
);

CREATE TABLE apr_requests (
    id                BIGSERIAL      PRIMARY KEY,
    organization_id   BIGINT         NOT NULL,
    document_id       BIGINT         NOT NULL,
    document_type     VARCHAR(40)    NOT NULL,
    business_unit_id  BIGINT,
    -- the DOCUMENT's team (what scoping reads) and the matrix that governs it (NULL = the
    -- default rule); usually related, never the same thing - see asfl-erp V27.
    owning_team_id    BIGINT,
    matrix_id         BIGINT,
    amount            NUMERIC(18, 2),
    raised_by_user_id BIGINT,
    raised_by         VARCHAR(100),
    current_level     INTEGER        NOT NULL DEFAULT 1,
    total_levels      INTEGER        NOT NULL,
    pending           BOOLEAN        NOT NULL DEFAULT TRUE,
    outcome           VARCHAR(10),
    settled_at        TIMESTAMP,
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,

    CONSTRAINT fk_apr_request_document FOREIGN KEY (document_id) REFERENCES gbl_business_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_apr_request_matrix FOREIGN KEY (matrix_id) REFERENCES apr_matrices (id),
    CONSTRAINT ck_apr_request_levels CHECK (total_levels >= 1 AND current_level >= 1 AND current_level <= total_levels)
);

-- At most one live request per document: pressing Submit twice is a message, not a second queue entry.
CREATE UNIQUE INDEX uq_apr_request_pending ON apr_requests (document_id) WHERE pending;
-- The inbox asks "everything pending here", not "is this one pending".
CREATE INDEX ix_apr_request_open ON apr_requests (organization_id, business_unit_id) WHERE pending;
CREATE INDEX ix_apr_request_document ON apr_requests (document_id);

ALTER TABLE apr_document_history
    ADD COLUMN request_id BIGINT,
    ADD COLUMN level      INTEGER;

-- ---------------------------------------------------------------------------------------------
-- V23's per-team approver list is replaced by team-wise matrices, which say the same thing and
-- more (levels, roles, amount bands). It never held a row outside development.
-- ---------------------------------------------------------------------------------------------
DROP TABLE org_marketing_team_approvers;

-- ---------------------------------------------------------------------------------------------
-- Screens. The inbox is for whoever approves anything; the matrices are maintained by whoever
-- maintains access, as the team master is.
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT DISTINCT g.role_id, 'APPROVALS', TRUE, FALSE, FALSE, FALSE, TRUE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
WHERE g.can_approve = TRUE
ON CONFLICT (role_id, screen_code) DO NOTHING;

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'APPROVAL_SETUP', g.can_view, g.can_create, g.can_amend, g.can_delete, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'SECURITY_ADMIN'
ON CONFLICT (role_id, screen_code) DO NOTHING;
