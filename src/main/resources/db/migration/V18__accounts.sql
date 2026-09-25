-- =============================================================================================
-- V18 - Accounts: chart of accounts, accounting periods, cost centres, posting rules, the general
-- ledger and customer credit limits.
--
-- Ported from asfl-erp's accounts module (its V10, V17, V20) and adapted to this schema's
-- conventions: organization-scoped rows, the acc_ prefix, VARCHAR audit columns, soft delete on
-- master data. The rules it enforces are the same:
--
--   * An entry balances before it exists (debits = credits, in the entry currency and in BDT).
--   * A CONTROL account (receivable, payable) is posted only with a party, never by hand journal.
--   * Nothing posts into a CLOSED period; reopening is recorded with a reason.
--   * A SUMMARY account (one with children) takes no entries.
--   * The ledger is append-only: a correction is a reversing entry, enforced here by trigger.
--
-- Posting rules are data: "a greige receipt debits greige stock and credits WIP" is a row an
-- accountant can read and change, not a branch in code.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- Chart of accounts
-- ---------------------------------------------------------------------------------------------
CREATE TABLE acc_accounts (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    account_type    VARCHAR(20)  NOT NULL,
    -- GENERAL posts normally; CONTROL is a party subledger control; SUMMARY has children and
    -- therefore takes no entries of its own.
    usage_type      VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    parent_id       BIGINT       REFERENCES acc_accounts (id),
    currency_code   VARCHAR(3),
    description     VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_acc_account_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_acc_account_type  CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    CONSTRAINT ck_acc_account_usage CHECK (usage_type IN ('GENERAL','CONTROL','SUMMARY')),
    CONSTRAINT ck_acc_account_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_acc_account_parent ON acc_accounts (parent_id) WHERE parent_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Accounting periods - one per month of the organization's fiscal year
-- ---------------------------------------------------------------------------------------------
CREATE TABLE acc_periods (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    fiscal_year     INT          NOT NULL,
    period_no       INT          NOT NULL,
    starts_on       DATE         NOT NULL,
    ends_on         DATE         NOT NULL,
    period_status   VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    closed_on       DATE,
    closed_by       VARCHAR(100),
    -- The close checklist no longer holds after a reopen, so the reason is kept and the status
    -- stays distinguishable from never-closed.
    reopen_reason   VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_acc_period_org_code UNIQUE (organization_id, code),
    CONSTRAINT uk_acc_period_in_year  UNIQUE (organization_id, fiscal_year, period_no),
    CONSTRAINT ck_acc_period_state CHECK (period_status IN ('OPEN','TEMPORARILY_OPEN','CLOSED')),
    CONSTRAINT ck_acc_period_no    CHECK (period_no BETWEEN 1 AND 12),
    CONSTRAINT ck_acc_period_dates CHECK (ends_on > starts_on)
);
CREATE INDEX ix_acc_period_covering ON acc_periods (organization_id, starts_on, ends_on);

-- ---------------------------------------------------------------------------------------------
-- Cost centres - where a cost lands (production shed, overhead pool, admin department)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE acc_cost_centres (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    centre_type     VARCHAR(20)  NOT NULL,
    parent_id       BIGINT       REFERENCES acc_cost_centres (id),
    absorbs_into_id BIGINT       REFERENCES acc_cost_centres (id),
    section_code    VARCHAR(40),
    remarks         VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_acc_cost_centre_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_acc_cost_centre_type CHECK (centre_type IN ('PRODUCTION','OVERHEAD','ADMINISTRATIVE','SERVICE'))
);

-- ---------------------------------------------------------------------------------------------
-- Posting rules - the event-to-entry table, as data. Effective-dated.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE acc_posting_rules (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    description     VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_acc_rule_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_acc_rule_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
-- At most one live rule per event: two would make a posting depend on row order.
CREATE UNIQUE INDEX uq_acc_rule_current ON acc_posting_rules (organization_id, event_type)
    WHERE effective_to IS NULL AND active AND NOT deleted;

CREATE TABLE acc_posting_rule_lines (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    rule_id         BIGINT       NOT NULL REFERENCES acc_posting_rules (id) ON DELETE CASCADE,
    sequence        INT          NOT NULL,
    side            VARCHAR(6)   NOT NULL,
    -- By code, so a chart can be rebuilt without rewriting every rule.
    account_code    VARCHAR(40)  NOT NULL,
    -- Which posting amount this leg draws: an invoice splits "net" and "vat".
    amount_key      VARCHAR(40)  NOT NULL DEFAULT 'amount',
    narration       VARCHAR(200),
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_acc_rule_line_seq UNIQUE (rule_id, sequence),
    CONSTRAINT ck_acc_rule_line_side CHECK (side IN ('DEBIT','CREDIT'))
);
CREATE INDEX ix_acc_rule_line_rule ON acc_posting_rule_lines (rule_id);

-- ---------------------------------------------------------------------------------------------
-- General ledger. Append-only - see the trigger below.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE acc_gl_entries (
    id                BIGSERIAL     PRIMARY KEY,
    organization_id   BIGINT        NOT NULL,
    business_unit_id  BIGINT,
    entry_no          VARCHAR(60)   NOT NULL,
    -- The document that caused this entry (polymorphic), or MANUAL for a hand journal.
    doc_type_code     VARCHAR(40)   NOT NULL,
    document_id       BIGINT,
    event_type        VARCHAR(60)   NOT NULL,
    posting_date      DATE          NOT NULL,
    period_id         BIGINT        NOT NULL REFERENCES acc_periods (id),
    currency_code     VARCHAR(3)    NOT NULL,
    -- Six decimals: a USD/BDT rate rounded to two bakes an error into the books at posting.
    fx_rate           NUMERIC(18,6) NOT NULL DEFAULT 1,
    narration         VARCHAR(1000),
    reverses_entry_id BIGINT        REFERENCES acc_gl_entries (id),
    reversed_by_id    BIGINT        REFERENCES acc_gl_entries (id),
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,
    CONSTRAINT uk_acc_gl_entry_no UNIQUE (organization_id, entry_no),
    CONSTRAINT ck_acc_gl_fx_positive CHECK (fx_rate > 0),
    CONSTRAINT ck_acc_gl_not_own_reversal CHECK (reverses_entry_id IS NULL OR reverses_entry_id <> id)
);
CREATE INDEX ix_acc_gl_entry_document ON acc_gl_entries (doc_type_code, document_id);
CREATE INDEX ix_acc_gl_entry_date     ON acc_gl_entries (organization_id, posting_date);

CREATE TABLE acc_gl_entry_lines (
    id                BIGSERIAL     PRIMARY KEY,
    organization_id   BIGINT        NOT NULL,
    entry_id          BIGINT        NOT NULL REFERENCES acc_gl_entries (id),
    sequence          INT           NOT NULL,
    account_code      VARCHAR(40)   NOT NULL,
    side              VARCHAR(6)    NOT NULL,
    -- Always positive; the sign lives in side.
    amount            NUMERIC(18,2) NOT NULL,
    functional_amount NUMERIC(18,2) NOT NULL,
    -- Required on a control-account line: the party subledger is a view over this column.
    party_id          BIGINT        REFERENCES pty_parties (id),
    cost_centre_code  VARCHAR(40),
    narration         VARCHAR(500),
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,
    CONSTRAINT uk_acc_gl_line_seq UNIQUE (entry_id, sequence),
    CONSTRAINT ck_acc_gl_line_side CHECK (side IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_acc_gl_line_positive CHECK (amount > 0 AND functional_amount > 0)
);
CREATE INDEX ix_acc_gl_line_entry   ON acc_gl_entry_lines (entry_id);
CREATE INDEX ix_acc_gl_line_account ON acc_gl_entry_lines (organization_id, account_code);
CREATE INDEX ix_acc_gl_line_party   ON acc_gl_entry_lines (party_id) WHERE party_id IS NOT NULL;

-- The ledger is append-only. The one permitted change is linking an entry to the entry that
-- reverses it (reversed_by_id, set once); everything else - and every delete - is refused.
CREATE OR REPLACE FUNCTION acc_gl_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'The general ledger is append-only: post a reversing entry instead of deleting %', OLD.id;
    END IF;
    IF TG_TABLE_NAME = 'acc_gl_entries'
       AND OLD.reversed_by_id IS NULL AND NEW.reversed_by_id IS NOT NULL
       AND (NEW.entry_no, NEW.posting_date, NEW.period_id, NEW.currency_code, NEW.fx_rate, NEW.event_type)
           IS NOT DISTINCT FROM
           (OLD.entry_no, OLD.posting_date, OLD.period_id, OLD.currency_code, OLD.fx_rate, OLD.event_type) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'The general ledger is append-only: post a reversing entry instead of editing %', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_acc_gl_entries_append_only
    BEFORE UPDATE OR DELETE ON acc_gl_entries
    FOR EACH ROW EXECUTE FUNCTION acc_gl_append_only();
CREATE TRIGGER tr_acc_gl_entry_lines_append_only
    BEFORE UPDATE OR DELETE ON acc_gl_entry_lines
    FOR EACH ROW EXECUTE FUNCTION acc_gl_append_only();

-- ---------------------------------------------------------------------------------------------
-- Customer credit limits - effective-dated; a hold is separate from the number
-- ---------------------------------------------------------------------------------------------
CREATE TABLE acc_credit_limits (
    id              BIGSERIAL     PRIMARY KEY,
    organization_id BIGINT        NOT NULL,
    party_id        BIGINT        NOT NULL REFERENCES pty_parties (id),
    currency_code   VARCHAR(3)    NOT NULL DEFAULT 'BDT',
    limit_amount    NUMERIC(18,2) NOT NULL,
    -- Exposure covered by an LC or bank guarantee, and therefore not at risk.
    secured_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    on_hold         BOOLEAN       NOT NULL DEFAULT FALSE,
    hold_reason     VARCHAR(300),
    effective_from  DATE          NOT NULL,
    effective_to    DATE,
    review_on       DATE,
    remarks         VARCHAR(500),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT ck_acc_credit_amounts CHECK (limit_amount >= 0 AND secured_amount >= 0),
    CONSTRAINT ck_acc_credit_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_acc_credit_hold_reason CHECK (NOT on_hold OR hold_reason IS NOT NULL)
);
CREATE UNIQUE INDEX uq_acc_credit_current ON acc_credit_limits (organization_id, party_id)
    WHERE effective_to IS NULL AND active AND NOT deleted;

-- ---------------------------------------------------------------------------------------------
-- Screen grants: the ROLE_ACCOUNTS shell (V11) gets the whole Accounts section.
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, s.code, TRUE, TRUE, TRUE, TRUE, TRUE, 0, 'seed', now()
FROM sec_fabric_roles r
CROSS JOIN (VALUES ('ACC_CHART'), ('ACC_JOURNAL'), ('ACC_REPORTS'), ('ACC_SETUP'), ('ACC_CREDIT')) AS s (code)
WHERE r.name = 'ROLE_ACCOUNTS'
ON CONFLICT (role_id, screen_code) DO NOTHING;
