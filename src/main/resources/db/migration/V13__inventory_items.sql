-- Item master and its reference lists, ported from SpindleERP's inventory.item package onto
-- this project's conventions: organization_id as a column (not a join), soft delete, @Version on
-- every row, every foreign key indexed at creation.
--
-- Corrections to SpindleERP's shape, each enforced here and not only in Java:
--   * yarn attributes sit on inv_items (no one-to-one yarn_items table), and ck_inv_item_yarn_spec
--     makes "YARN if and only if type, count, ply and blend are all set" a database fact;
--   * ux_inv_item_yarn_identity - no two live, active yarns share type + count + ply + blend
--     (SpindleERP checked that in Java only);
--   * ux_inv_uom_base_per_category - one base unit per UoM category; conversions are relative to
--     it, so a second one made every factor ambiguous (SpindleERP allowed it);
--   * ck_yrn_bc_percentage - a component share is above 0 and at most 100.
--
-- Seeded only with values recovered from the live asgdynamic screens
-- (business-logic-capture/master_data_catalog.json), for the 'ASG' organization.

-- =============================================================================================
-- Units of measure
-- =============================================================================================

CREATE TABLE inv_uoms (
    id                BIGSERIAL      PRIMARY KEY,
    organization_id   BIGINT         NOT NULL,
    code              VARCHAR(20)    NOT NULL,
    name              VARCHAR(100)   NOT NULL,
    symbol            VARCHAR(20),
    category          VARCHAR(30)    NOT NULL,
    base_unit         BOOLEAN        NOT NULL DEFAULT FALSE,
    conversion_factor NUMERIC(18,10) NOT NULL DEFAULT 1,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,
    CONSTRAINT uk_inv_uom_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_inv_uom_factor CHECK (conversion_factor > 0),
    CONSTRAINT ck_inv_uom_base_factor CHECK (NOT base_unit OR conversion_factor = 1)
);
CREATE INDEX ix_inv_uom_lookup ON inv_uoms (organization_id, category, active);
CREATE UNIQUE INDEX ux_inv_uom_base_per_category ON inv_uoms (organization_id, category)
    WHERE base_unit AND NOT deleted;

-- =============================================================================================
-- HS codes
-- =============================================================================================

CREATE TABLE inv_hs_codes (
    id                         BIGSERIAL    PRIMARY KEY,
    organization_id            BIGINT       NOT NULL,
    hs_code                    VARCHAR(20)  NOT NULL,
    description                VARCHAR(500),
    short_description          VARCHAR(200),
    hs_type                    VARCHAR(20)  NOT NULL DEFAULT 'BOTH',
    customs_duty_percent       NUMERIC(6,2),
    vat_percent                NUMERIC(6,2),
    supplementary_duty_percent NUMERIC(6,2),
    ait_percent                NUMERIC(6,2),
    bonded_allowed             BOOLEAN      NOT NULL DEFAULT TRUE,
    requires_export_permit     BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_import_permit     BOOLEAN      NOT NULL DEFAULT FALSE,
    active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted                    BOOLEAN      NOT NULL DEFAULT FALSE,
    version                    BIGINT,
    created_by                 VARCHAR(100),
    created_at                 TIMESTAMP,
    updated_by                 VARCHAR(100),
    updated_at                 TIMESTAMP,
    CONSTRAINT uk_inv_hs_org_code UNIQUE (organization_id, hs_code)
);

-- =============================================================================================
-- Item categories: ROOT -> GROUP -> ITEM
-- =============================================================================================

CREATE TABLE inv_item_categories (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(50)  NOT NULL,
    prefix          VARCHAR(20),
    name            VARCHAR(100) NOT NULL,
    layer           VARCHAR(20)  NOT NULL,
    item_type       VARCHAR(30),
    parent_id       BIGINT,
    description     TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_inv_cat_org_code UNIQUE (organization_id, code),
    CONSTRAINT fk_inv_cat_parent FOREIGN KEY (parent_id) REFERENCES inv_item_categories (id),
    CONSTRAINT ck_inv_cat_layer CHECK (layer IN ('ROOT', 'GROUP', 'ITEM')),
    CONSTRAINT ck_inv_cat_root_has_no_parent CHECK ((layer = 'ROOT') = (parent_id IS NULL))
);
CREATE INDEX ix_inv_cat_parent ON inv_item_categories (parent_id);

-- =============================================================================================
-- Brands and models
-- =============================================================================================

CREATE TABLE inv_item_brands (
    id                BIGSERIAL    PRIMARY KEY,
    organization_id   BIGINT       NOT NULL,
    code              VARCHAR(30)  NOT NULL,
    name              VARCHAR(150) NOT NULL,
    short_name        VARCHAR(50),
    country_of_origin VARCHAR(100),
    description       TEXT,
    approved          BOOLEAN      NOT NULL DEFAULT FALSE,
    approved_by       VARCHAR(100),
    approved_at       TIMESTAMP,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,
    CONSTRAINT uk_inv_brand_org_code UNIQUE (organization_id, code)
);

CREATE TABLE inv_item_models (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    brand_id        BIGINT,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    short_name      VARCHAR(50),
    description     TEXT,
    approved        BOOLEAN      NOT NULL DEFAULT FALSE,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMP,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_inv_model_org_code UNIQUE (organization_id, code),
    CONSTRAINT fk_inv_model_brand FOREIGN KEY (brand_id) REFERENCES inv_item_brands (id)
);
CREATE INDEX ix_inv_model_brand ON inv_item_models (brand_id);

-- =============================================================================================
-- Yarn reference lists
-- =============================================================================================

CREATE TABLE yrn_types (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    short_name      VARCHAR(30),
    description     TEXT,
    approved        BOOLEAN      NOT NULL DEFAULT FALSE,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMP,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_yrn_type_org_code UNIQUE (organization_id, code)
);

CREATE TABLE yrn_counts (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    approved        BOOLEAN      NOT NULL DEFAULT FALSE,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMP,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_yrn_count_org_code UNIQUE (organization_id, code)
);

CREATE TABLE yrn_plies (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    ply_number      INTEGER      NOT NULL,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    description     TEXT,
    approved        BOOLEAN      NOT NULL DEFAULT FALSE,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMP,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_yrn_ply_org_code UNIQUE (organization_id, code),
    CONSTRAINT uk_yrn_ply_org_number UNIQUE (organization_id, ply_number),
    CONSTRAINT ck_yrn_ply_number CHECK (ply_number >= 1)
);

CREATE TABLE yrn_blends (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(500) NOT NULL,
    short_name      VARCHAR(500),
    description     TEXT,
    approved        BOOLEAN      NOT NULL DEFAULT FALSE,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMP,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT uk_yrn_blend_org_code UNIQUE (organization_id, code)
);
CREATE UNIQUE INDEX ux_yrn_blend_org_name ON yrn_blends (organization_id, lower(name)) WHERE NOT deleted;

-- =============================================================================================
-- Items
-- =============================================================================================

CREATE TABLE inv_items (
    id                   BIGSERIAL     PRIMARY KEY,
    organization_id      BIGINT        NOT NULL,
    item_code            VARCHAR(50)   NOT NULL,
    item_name            VARCHAR(200)  NOT NULL,
    item_name_bn         VARCHAR(200),
    description          TEXT,
    item_type            VARCHAR(30)   NOT NULL,
    category_id          BIGINT        NOT NULL,
    base_uom_id          BIGINT        NOT NULL,
    hs_code_id           BIGINT,
    brand_id             BIGINT,
    model_id             BIGINT,
    barcode              VARCHAR(100),
    sku                  VARCHAR(100),

    reorder_level        NUMERIC(12,3),
    minimum_stock        NUMERIC(12,3),
    maximum_stock        NUMERIC(12,3),
    unit_price           NUMERIC(12,4),
    cost_price           NUMERIC(12,4),
    tax_rate             NUMERIC(5,2),

    fiber_type           VARCHAR(30),
    origin_name          VARCHAR(100),
    grade                VARCHAR(50),
    staple_length        NUMERIC(8,2),
    micronaire           NUMERIC(8,2),
    strength             NUMERIC(8,2),
    moisture             NUMERIC(8,2),
    trash                NUMERIC(5,2),
    purity               NUMERIC(5,2),

    yarn_type_id         BIGINT,
    yarn_count_id        BIGINT,
    yarn_ply_id          BIGINT,
    yarn_blend_id        BIGINT,
    quality_grade        VARCHAR(100),

    chemical_formula     VARCHAR(50),
    cas_number           VARCHAR(50),
    hazardous            BOOLEAN       NOT NULL DEFAULT FALSE,
    safety_data_sheet    VARCHAR(100),
    concentration        NUMERIC(8,2),
    expiry_date          DATE,

    manufacturer         VARCHAR(100),
    model_number         VARCHAR(100),
    serial_number        VARCHAR(50),
    warranty_months      INTEGER,
    asset_value          NUMERIC(15,2),
    depreciation_rate    NUMERIC(5,2),

    process_loss_percent NUMERIC(5,2),
    yield_percent        NUMERIC(5,2),
    standard_cost_per_kg NUMERIC(12,2),
    selling_price_per_kg NUMERIC(12,2),

    approved             BOOLEAN       NOT NULL DEFAULT FALSE,
    approved_by          VARCHAR(100),
    approved_at          TIMESTAMP,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted              BOOLEAN       NOT NULL DEFAULT FALSE,
    version              BIGINT,
    created_by           VARCHAR(100),
    created_at           TIMESTAMP,
    updated_by           VARCHAR(100),
    updated_at           TIMESTAMP,

    CONSTRAINT uk_inv_item_org_code UNIQUE (organization_id, item_code),
    CONSTRAINT fk_inv_item_category   FOREIGN KEY (category_id)   REFERENCES inv_item_categories (id),
    CONSTRAINT fk_inv_item_uom        FOREIGN KEY (base_uom_id)   REFERENCES inv_uoms (id),
    CONSTRAINT fk_inv_item_hs         FOREIGN KEY (hs_code_id)    REFERENCES inv_hs_codes (id),
    CONSTRAINT fk_inv_item_brand      FOREIGN KEY (brand_id)      REFERENCES inv_item_brands (id),
    CONSTRAINT fk_inv_item_model      FOREIGN KEY (model_id)      REFERENCES inv_item_models (id),
    CONSTRAINT fk_inv_item_yarn_type  FOREIGN KEY (yarn_type_id)  REFERENCES yrn_types (id),
    CONSTRAINT fk_inv_item_yarn_count FOREIGN KEY (yarn_count_id) REFERENCES yrn_counts (id),
    CONSTRAINT fk_inv_item_yarn_ply   FOREIGN KEY (yarn_ply_id)   REFERENCES yrn_plies (id),
    CONSTRAINT fk_inv_item_yarn_blend FOREIGN KEY (yarn_blend_id) REFERENCES yrn_blends (id),
    CONSTRAINT ck_inv_item_yarn_spec CHECK (
        (item_type = 'YARN') = (yarn_type_id IS NOT NULL AND yarn_count_id IS NOT NULL
                                AND yarn_ply_id IS NOT NULL AND yarn_blend_id IS NOT NULL)
        AND (item_type = 'YARN' OR (yarn_type_id IS NULL AND yarn_count_id IS NULL
                                    AND yarn_ply_id IS NULL AND yarn_blend_id IS NULL))),
    CONSTRAINT ck_inv_item_stock_range CHECK (
        minimum_stock IS NULL OR maximum_stock IS NULL OR minimum_stock <= maximum_stock)
);
CREATE INDEX ix_inv_item_lookup     ON inv_items (organization_id, item_type, active);
CREATE INDEX ix_inv_item_category   ON inv_items (category_id);
CREATE INDEX ix_inv_item_uom        ON inv_items (base_uom_id);
CREATE INDEX ix_inv_item_hs         ON inv_items (hs_code_id);
CREATE INDEX ix_inv_item_brand      ON inv_items (brand_id);
CREATE INDEX ix_inv_item_model      ON inv_items (model_id);
CREATE INDEX ix_inv_item_yarn_type  ON inv_items (yarn_type_id);
CREATE INDEX ix_inv_item_yarn_count ON inv_items (yarn_count_id);
CREATE INDEX ix_inv_item_yarn_ply   ON inv_items (yarn_ply_id);
CREATE INDEX ix_inv_item_yarn_blend ON inv_items (yarn_blend_id);
CREATE UNIQUE INDEX ux_inv_item_org_name ON inv_items (organization_id, lower(item_name)) WHERE NOT deleted;
CREATE UNIQUE INDEX ux_inv_item_yarn_identity
    ON inv_items (organization_id, yarn_type_id, yarn_count_id, yarn_ply_id, yarn_blend_id)
    WHERE item_type = 'YARN' AND active AND NOT deleted;

-- =============================================================================================
-- Blend components (reference items, so created after inv_items)
-- =============================================================================================

CREATE TABLE yrn_blend_components (
    id              BIGSERIAL    PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    blend_id        BIGINT       NOT NULL,
    fiber_item_id   BIGINT       NOT NULL,
    percentage      NUMERIC(5,2) NOT NULL,
    certification   VARCHAR(50),
    display_order   INTEGER      NOT NULL,
    remarks         TEXT,
    version         BIGINT,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    CONSTRAINT fk_yrn_bc_blend FOREIGN KEY (blend_id)      REFERENCES yrn_blends (id) ON DELETE CASCADE,
    CONSTRAINT fk_yrn_bc_fiber FOREIGN KEY (fiber_item_id) REFERENCES inv_items (id),
    CONSTRAINT ck_yrn_bc_percentage CHECK (percentage > 0 AND percentage <= 100)
);
CREATE INDEX ix_yrn_bc_blend ON yrn_blend_components (blend_id);
CREATE INDEX ix_yrn_bc_fiber ON yrn_blend_components (fiber_item_id);

-- =============================================================================================
-- Seed: legacy reference values (organization 'ASG')
-- =============================================================================================

-- Units: the legacy U100-U118 list, codes kept. Category, base unit and factor are standard
-- definitions added here (the legacy list carried names only). Ton is the metric tonne and
-- Gallon the US gallon - confirm both before stock is converted with them.
INSERT INTO inv_uoms (organization_id, code, name, symbol, category, base_unit, conversion_factor, version, created_by, created_at)
SELECT o.id, v.code, v.name, v.symbol, v.category, v.base_unit, v.factor, 0, 'seed', now()
FROM org_organizations o,
     (VALUES
        ('U100', 'Kilometer',  'km',  'LENGTH', FALSE, 1000::numeric),
        ('U101', 'Meter',      'm',   'LENGTH', TRUE,  1),
        ('U102', 'PCS',        'pcs', 'COUNT',  TRUE,  1),
        ('U103', 'Centimeter', 'cm',  'LENGTH', FALSE, 0.01),
        ('U104', 'Millimeter', 'mm',  'LENGTH', FALSE, 0.001),
        ('U105', 'Feet',       'ft',  'LENGTH', FALSE, 0.3048),
        ('U106', 'Yard',       'yd',  'LENGTH', FALSE, 0.9144),
        ('U107', 'Inch',       'in',  'LENGTH', FALSE, 0.0254),
        ('U108', 'Mile',       'mi',  'LENGTH', FALSE, 1609.344),
        ('U109', 'Gram',       'g',   'WEIGHT', FALSE, 0.001),
        ('U110', 'Milligram',  'mg',  'WEIGHT', FALSE, 0.000001),
        ('U111', 'Kilogram',   'kg',  'WEIGHT', TRUE,  1),
        ('U112', 'Ounce',      'oz',  'WEIGHT', FALSE, 0.028349523125),
        ('U113', 'Pound',      'lb',  'WEIGHT', FALSE, 0.45359237),
        ('U114', 'Ton',        't',   'WEIGHT', FALSE, 1000),
        ('U115', 'Litre',      'L',   'VOLUME', TRUE,  1),
        ('U116', 'Millilitre', 'mL',  'VOLUME', FALSE, 0.001),
        ('U117', 'Kilolitre',  'kL',  'VOLUME', FALSE, 1000),
        ('U118', 'Gallon',     'gal', 'VOLUME', FALSE, 3.785411784)
     ) AS v(code, name, symbol, category, base_unit, factor)
WHERE o.code = 'ASG';

-- HS codes: the 13 fabric headings in use; the legacy system held no descriptions or rates.
INSERT INTO inv_hs_codes (organization_id, hs_code, hs_type, version, created_by, created_at)
SELECT o.id, v.code, 'BOTH', 0, 'seed', now()
FROM org_organizations o,
     (VALUES ('5208.11.00'), ('5208.12.00'), ('5208.13.00'), ('5208.19.00'), ('5208.32.00'),
             ('5209.11.00'), ('5209.12.00'), ('5209.19.00'),
             ('5210.11.00'), ('5210.19.00'), ('5210.31.00'),
             ('5211.11.00'), ('5211.19.00')) AS v(code)
WHERE o.code = 'ASG';

-- Category roots, legacy codes kept. Groups and item-level categories are not seeded: the
-- legacy capture names them but does not say which root each sits under or what code it has,
-- and a guessed code could collide with a real one when legacy data is loaded.
INSERT INTO inv_item_categories (organization_id, code, prefix, name, layer, version, created_by, created_at)
SELECT o.id, v.code, 'CAF', v.name, 'ROOT', 0, 'seed', now()
FROM org_organizations o,
     (VALUES ('CAF110000', 'Raw Material'),
             ('CAF120000', 'Finish Goods'),
             ('CAF130000', 'Maintenance Repair And Operating'),
             ('CAF140000', 'General Item'),
             ('CAF150000', 'Non current Fixed Assets'),
             ('CAF160000', 'Civil And Construction')) AS v(code, name)
WHERE o.code = 'ASG';

-- Brand, legacy code kept.
INSERT INTO inv_item_brands (organization_id, code, name, approved, approved_by, approved_at, version, created_by, created_at)
SELECT o.id, 'BRAF00001', 'Xianho', TRUE, 'seed', now(), 0, 'seed', now()
FROM org_organizations o WHERE o.code = 'ASG';

-- Yarn lists. The legacy codes were not captured, so these take the codes the application
-- would have issued, and the counters below continue after them.
INSERT INTO yrn_types (organization_id, code, name, short_name, approved, approved_by, approved_at, version, created_by, created_at)
SELECT o.id, v.code, v.name, v.short_name, TRUE, 'seed', now(), 0, 'seed', now()
FROM org_organizations o,
     (VALUES ('YTAF0001', 'Card', 'CD'), ('YTAF0002', 'Combed', 'CM')) AS v(code, name, short_name)
WHERE o.code = 'ASG';

INSERT INTO yrn_counts (organization_id, code, name, approved, approved_by, approved_at, version, created_by, created_at)
SELECT o.id, 'YCAF0001', '30', TRUE, 'seed', now(), 0, 'seed', now()
FROM org_organizations o WHERE o.code = 'ASG';

INSERT INTO yrn_plies (organization_id, ply_number, code, name, approved, approved_by, approved_at, version, created_by, created_at)
SELECT o.id, 1, 'YPAF0001', 'Single', TRUE, 'seed', now(), 0, 'seed', now()
FROM org_organizations o WHERE o.code = 'ASG';

INSERT INTO gbl_document_sequence (organization_id, seq_key, last_value)
SELECT o.id, v.seq_key, v.last_value
FROM org_organizations o, (VALUES ('YTAF', 2), ('YCAF', 1), ('YPAF', 1)) AS v(seq_key, last_value)
WHERE o.code = 'ASG'
ON CONFLICT (organization_id, seq_key) DO UPDATE
    SET last_value = GREATEST(gbl_document_sequence.last_value, EXCLUDED.last_value);

-- Not seeded, because they need item-level categories first: the four fibers (Viscose, Tencel,
-- Cotton, Linen), the three blends built from them, and the yarn items. See README.

-- =============================================================================================
-- Screen grants for the two new screens, on the two working roles V8 set up
-- =============================================================================================

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, s.screen_code, TRUE, TRUE, TRUE, TRUE, FALSE, 0, 'seed', now()
FROM sec_fabric_roles r, (VALUES ('ITEM'), ('ITEM_SETUP')) AS s(screen_code)
WHERE r.name = 'Fabric Operations'
ON CONFLICT (role_id, screen_code) DO NOTHING;

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, s.screen_code, TRUE, FALSE, FALSE, FALSE, TRUE, 0, 'seed', now()
FROM sec_fabric_roles r, (VALUES ('ITEM'), ('ITEM_SETUP')) AS s(screen_code)
WHERE r.name = 'Document Approver'
ON CONFLICT (role_id, screen_code) DO NOTHING;
