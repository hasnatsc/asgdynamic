-- Party master (ported from asfl-erp's party module) and real foreign keys behind every
-- association BusinessDocument and its lines now map as an object rather than a bare id.
--
-- Parties are org-scoped like every other master here (asfl-erp scopes them by business unit),
-- and keep asfl-erp's shape: one row per company, a role row per capacity it acts in, and
-- addresses, contacts and bank accounts as rows rather than column groups.

-- =============================================================================================
-- Parties
-- =============================================================================================

CREATE TABLE pty_parties (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    party_type      VARCHAR(20)  NOT NULL DEFAULT 'ORGANISATION',
    legal_name      VARCHAR(300),
    country_code    VARCHAR(40),
    -- Statutory identifiers are typed columns because compliance reporting filters on them.
    -- Everything sparser - IRC, ERC, bond licence, BTMA membership - goes in attributes.
    tin             VARCHAR(40),
    bin             VARCHAR(40),
    vat_reg_no      VARCHAR(40),
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_pty_party_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_pty_party_type CHECK (party_type IN ('ORGANISATION', 'INDIVIDUAL'))
);

CREATE TABLE pty_party_roles (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    party_id        BIGINT       NOT NULL,
    role_type       VARCHAR(20)  NOT NULL,
    -- MARKETING | COMMERCIAL on a customer; NULL on every other role. The distinction the
    -- legacy schema encoded as two separate customer tables.
    qualifier       VARCHAR(20),
    role_code       VARCHAR(40),
    granted_on      DATE         NOT NULL,
    revoked_on      DATE,
    is_current      BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT fk_pty_role_party FOREIGN KEY (party_id) REFERENCES pty_parties (id) ON DELETE CASCADE,
    -- NULLS NOT DISTINCT (PostgreSQL 15+): qualifier is NULL on every role but CUSTOMER, and a
    -- plain UNIQUE would let a party hold SUPPLIER twice. Party.grantRole restores instead.
    CONSTRAINT uk_pty_role UNIQUE NULLS NOT DISTINCT (party_id, role_type, qualifier),
    CONSTRAINT ck_pty_role_type CHECK (role_type IN
        ('CUSTOMER', 'SUPPLIER', 'AGENT', 'BANK', 'EMPLOYEE', 'BRAND', 'BUYING_HOUSE', 'GARMENT_FACTORY')),
    CONSTRAINT ck_pty_role_qualifier CHECK (
        (role_type = 'CUSTOMER' AND qualifier IN ('MARKETING', 'COMMERCIAL'))
        OR (role_type <> 'CUSTOMER' AND qualifier IS NULL)),
    CONSTRAINT ck_pty_role_revoked_after_granted CHECK (revoked_on IS NULL OR revoked_on >= granted_on)
);
CREATE INDEX ix_pty_role_party ON pty_party_roles (party_id);
-- The picker asks "which parties currently hold role X".
CREATE INDEX ix_pty_role_lookup ON pty_party_roles (role_type, party_id) WHERE is_current;

CREATE TABLE pty_party_addresses (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    party_id        BIGINT       NOT NULL,
    address_type    VARCHAR(20)  NOT NULL,
    line1           VARCHAR(300) NOT NULL,
    line2           VARCHAR(300),
    geo_code        VARCHAR(40),
    postcode        VARCHAR(20),
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT fk_pty_address_party FOREIGN KEY (party_id) REFERENCES pty_parties (id) ON DELETE CASCADE,
    CONSTRAINT ck_pty_address_type CHECK (address_type IN
        ('REGISTERED', 'BILLING', 'SHIPPING', 'FACTORY', 'WAREHOUSE'))
);
CREATE INDEX ix_pty_address_party ON pty_party_addresses (party_id);

CREATE TABLE pty_party_contacts (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    party_id         BIGINT       NOT NULL,
    name             VARCHAR(200) NOT NULL,
    designation_code VARCHAR(40),
    phone            VARCHAR(40),
    mobile           VARCHAR(40),
    email            VARCHAR(200),
    is_primary       BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,
    CONSTRAINT fk_pty_contact_party FOREIGN KEY (party_id) REFERENCES pty_parties (id) ON DELETE CASCADE
);
CREATE INDEX ix_pty_contact_party ON pty_party_contacts (party_id);

CREATE TABLE pty_party_bank_accounts (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    party_id        BIGINT       NOT NULL,
    -- The bank is itself a party holding BANK: what removes fifteen bank columns from every
    -- party row, and the separate bank table with them.
    bank_party_id   BIGINT       NOT NULL,
    account_name    VARCHAR(200) NOT NULL,
    account_number  VARCHAR(60)  NOT NULL,
    branch_name     VARCHAR(200),
    routing_number  VARCHAR(40),
    swift_code      VARCHAR(20),
    currency_code   VARCHAR(3),
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT fk_pty_account_party FOREIGN KEY (party_id)      REFERENCES pty_parties (id) ON DELETE CASCADE,
    CONSTRAINT fk_pty_account_bank  FOREIGN KEY (bank_party_id) REFERENCES pty_parties (id),
    CONSTRAINT uk_pty_account_per_bank UNIQUE (party_id, bank_party_id, account_number),
    CONSTRAINT ck_pty_account_not_own_bank CHECK (party_id <> bank_party_id)
);
-- party_id is covered by the unique constraint's leading column.
CREATE INDEX ix_pty_account_bank ON pty_party_bank_accounts (bank_party_id);

-- =============================================================================================
-- Document associations: real foreign keys
-- =============================================================================================
--
-- parent_document_id, revision_of_id (V1) and source_color_line_id (V6) already had one.
-- These did not, because until now they pointed at nothing this schema held (party) or were
-- treated as plain ids. NOT VALID: PostgreSQL enforces them on every insert and update from
-- here on, but does not fail this migration over rows written before - a party_id stored before
-- pty_parties existed cannot reference it. Once legacy rows are clean, make them fully valid:
--   ALTER TABLE gbl_business_documents VALIDATE CONSTRAINT fk_gbd_party;   (and so on)

ALTER TABLE gbl_business_documents
    ADD CONSTRAINT fk_gbd_business_unit  FOREIGN KEY (business_unit_id)  REFERENCES org_business_units (id)  NOT VALID,
    ADD CONSTRAINT fk_gbd_warehouse      FOREIGN KEY (warehouse_id)      REFERENCES org_warehouses (id)      NOT VALID,
    ADD CONSTRAINT fk_gbd_party          FOREIGN KEY (party_id)          REFERENCES pty_parties (id)         NOT VALID,
    ADD CONSTRAINT fk_gbd_marketing_team FOREIGN KEY (marketing_team_id) REFERENCES org_marketing_teams (id) NOT VALID;

ALTER TABLE gbl_business_document_line_groups
    ADD CONSTRAINT fk_gbdlg_item FOREIGN KEY (item_id) REFERENCES inv_items (id) NOT VALID,
    ADD CONSTRAINT fk_gbdlg_uom  FOREIGN KEY (uom_id)  REFERENCES inv_uoms (id)  NOT VALID;

-- Indexes for the foreign keys no index led with. party_id, marketing_team_id and item_id
-- already had theirs (V1, V12, V6); business_unit_id only sat second in ix_gbd_org_unit_date.
CREATE INDEX IF NOT EXISTS ix_gbd_business_unit ON gbl_business_documents (business_unit_id);
CREATE INDEX IF NOT EXISTS ix_gbd_warehouse     ON gbl_business_documents (warehouse_id);
CREATE INDEX IF NOT EXISTS ix_gbdlg_uom         ON gbl_business_document_line_groups (uom_id);
