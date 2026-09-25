-- =============================================================================================
-- Business numbering: configurable, financial-year aware, never reissued.
--
-- Replaces gbl_document_sequence (one counter per {TYPE}{UNIT}, a fixed BPOAF000001 layout) with:
--   gbl_numbering_schemes  how each organization numbers each series - prefix, pattern, width,
--                          when it restarts, whether each branch counts on its own
--   gbl_number_counters    the counters, one row per (organization, series, branch, period)
--   gbl_issued_numbers     every number ever issued, unique per organization - the reason a
--                          number cannot come back after its record is cancelled, whatever the
--                          configuration becomes later
--
-- Default layout {PREFIX}-{FY}-{SEQ}: BK-2026-000001, CUS-2026-000001, VCH-2026-000001.
-- Scheme rows are created from the application's defaults on first use; see BusinessNumberService.
-- =============================================================================================

-- The financial year starts on the first of this month. 1 = the calendar year; Bangladesh's
-- statutory year starts in July (7).
ALTER TABLE org_organizations
    ADD COLUMN fiscal_year_start_month SMALLINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_org_fiscal_year_start_month CHECK (fiscal_year_start_month BETWEEN 1 AND 12);

-- ---------------------------------------------------------------------------------------------
-- Schemes. The CHECKs are the rules that make a configuration unable to repeat a number, so a
-- row written by hand is held to them as well as one saved through the screen.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE gbl_numbering_schemes (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    series_code     VARCHAR(40)  NOT NULL,
    prefix          VARCHAR(12)  NOT NULL,
    pattern         VARCHAR(80)  NOT NULL,
    sequence_width  INTEGER      NOT NULL,
    reset_policy    VARCHAR(20)  NOT NULL,
    counter_scope   VARCHAR(20)  NOT NULL,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,

    CONSTRAINT uk_gns_org_series UNIQUE (organization_id, series_code),
    -- Two series sharing a prefix could issue the same number; one series per prefix.
    CONSTRAINT uk_gns_org_prefix UNIQUE (organization_id, prefix),
    CONSTRAINT ck_gns_prefix  CHECK (prefix ~ '^[A-Z][A-Z0-9]{0,11}$'),
    CONSTRAINT ck_gns_width   CHECK (sequence_width BETWEEN 3 AND 12),
    CONSTRAINT ck_gns_reset   CHECK (reset_policy IN ('NEVER', 'FINANCIAL_YEAR', 'CALENDAR_YEAR')),
    CONSTRAINT ck_gns_scope   CHECK (counter_scope IN ('ORGANIZATION', 'BRANCH')),
    CONSTRAINT ck_gns_pattern CHECK (pattern ~ '^([A-Z0-9/_.-]|\{(PREFIX|BRANCH|FY|YYYY|YY|SEQ)\})+$'
                                     AND pattern LIKE '%{PREFIX}%' AND pattern LIKE '%{SEQ}%'),
    -- Separate counters are only unique if the number says which counter it came from.
    CONSTRAINT ck_gns_branch_in_pattern CHECK (counter_scope <> 'BRANCH' OR pattern LIKE '%{BRANCH}%'),
    CONSTRAINT ck_gns_fy_in_pattern     CHECK (reset_policy <> 'FINANCIAL_YEAR' OR pattern LIKE '%{FY}%'),
    CONSTRAINT ck_gns_year_in_pattern   CHECK (reset_policy <> 'CALENDAR_YEAR'
                                               OR pattern LIKE '%{YYYY}%' OR pattern LIKE '%{YY}%')
);

-- ---------------------------------------------------------------------------------------------
-- Counters. business_unit_id 0 is the organization-wide counter; period_key '' never resets,
-- otherwise FY2026 / CY2026. A new period is a new row, so a reset is an insert that
-- ON CONFLICT serializes - never an update racing a read.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE gbl_number_counters (
    organization_id  BIGINT      NOT NULL,
    series_code      VARCHAR(40) NOT NULL,
    business_unit_id BIGINT      NOT NULL DEFAULT 0,
    period_key       VARCHAR(12) NOT NULL DEFAULT '',
    last_value       BIGINT      NOT NULL,
    updated_at       TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_gbl_number_counters PRIMARY KEY (organization_id, series_code, business_unit_id, period_key),
    CONSTRAINT ck_gnc_last_value CHECK (last_value >= 0)
);

COMMENT ON TABLE gbl_number_counters IS
    'Last value issued per (organization, series, branch or 0, period). Only ever increases. '
    'To continue a legacy series, set last_value to its high-water mark; numbers already in '
    'gbl_issued_numbers are skipped regardless.';

-- ---------------------------------------------------------------------------------------------
-- Ledger. Written in the same short transaction as the counter increment, so a number is either
-- recorded here or was never handed out. Rows are never deleted.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE gbl_issued_numbers (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    number           VARCHAR(60)  NOT NULL,
    series_code      VARCHAR(40)  NOT NULL,
    business_unit_id BIGINT,
    period_key       VARCHAR(12),
    -- NULL for numbers typed by hand or carried over from before this table existed.
    sequence_value   BIGINT,
    issued_by        VARCHAR(100),
    issued_at        TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uk_gin_org_number UNIQUE (organization_id, number)
);

CREATE INDEX ix_gin_org_series ON gbl_issued_numbers (organization_id, series_code, issued_at);

-- Every number already in circulation, soft-deleted rows included: a cancelled document's number
-- stays spent. Whatever layout an organization configures later, the generator skips these.
INSERT INTO gbl_issued_numbers (organization_id, number, series_code, business_unit_id, issued_by, issued_at)
SELECT organization_id, number, series_code, business_unit_id, 'V17 backfill', now()
FROM (
    SELECT organization_id, document_no AS number, document_type AS series_code, business_unit_id FROM gbl_business_documents
    UNION ALL SELECT organization_id, code,      'PARTY',      NULL FROM pty_parties
    UNION ALL SELECT organization_id, role_code, role_type,    NULL FROM pty_party_roles WHERE role_code IS NOT NULL
    UNION ALL SELECT organization_id, item_code, 'ITEM',       NULL FROM inv_items
    UNION ALL SELECT organization_id, code,      'ITEM_BRAND', NULL FROM inv_item_brands
    UNION ALL SELECT organization_id, code,      'ITEM_MODEL', NULL FROM inv_item_models
    UNION ALL SELECT organization_id, code,      'YARN_TYPE',  NULL FROM yrn_types
    UNION ALL SELECT organization_id, code,      'YARN_COUNT', NULL FROM yrn_counts
    UNION ALL SELECT organization_id, code,      'YARN_PLY',   NULL FROM yrn_plies
    UNION ALL SELECT organization_id, code,      'YARN_BLEND', NULL FROM yrn_blends
) issued
ON CONFLICT (organization_id, number) DO NOTHING;

-- Superseded. Every number it produced is now in gbl_issued_numbers, so none can be reissued.
DROP TABLE gbl_document_sequence;

-- ---------------------------------------------------------------------------------------------
-- A customer / supplier / employee code identifies one party in that capacity. The qualifier is
-- part of the key because the legacy marketing and commercial customer lists were numbered
-- separately; the application gives one company the same code on both.
-- ---------------------------------------------------------------------------------------------
CREATE UNIQUE INDEX ux_pty_role_org_code ON pty_party_roles (organization_id, role_type, qualifier, role_code)
    NULLS NOT DISTINCT WHERE role_code IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- The numbering setup screen, for whoever administers security today.
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, 'NUMBERING', TRUE, FALSE, TRUE, FALSE, FALSE, 0, 'seed', now()
FROM sec_fabric_roles r
WHERE r.name = 'Fabric Operations'
ON CONFLICT (role_id, screen_code) DO NOTHING;
