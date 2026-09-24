-- Replaces the flat gbl_business_document_lines table with the two-level structure a real
-- production Booking payload showed was actually needed: one fabric-spec GROUP (asgdynamic's
-- dtlSet — construction, weave, composition, entered once) containing several COLOUR lines
-- (dtlLine — colour, quantity, price, and reference fields genuinely different per colour:
-- fabrics_style, lab_dip_reference, strike_off_reference, color_reference, loom_reference).
--
-- Why this is a DROP + CREATE, not an ALTER, and why V1 is not simply rewritten:
--   - No real environment has ever run V1-V5; every verification against them was a scratch
--     database dropped immediately after. There is no data anywhere to migrate.
--   - V1 is still not edited in place, for the same reason V4 was additive rather than a
--     rewrite: once a migration is written it is treated as shipped, so anyone extending
--     this project inherits one consistent rule rather than "except that one time the
--     foundation was wrong." A genuinely broken foundation gets a loud, explained correction
--     migration like this one — not a silent rewrite of history.
--
-- Also folds in FabricSpec's expanded field set (yarn counts/ratios, EPI/PPI, shrinkages,
-- GSM before/after wash, wash type/instruction, end use, DISPO reference) confirmed present
-- on the same real payload but missing from V1's narrower version.

DROP TABLE IF EXISTS gbl_business_document_lines;

-- ---------------------------------------------------------------------------
-- Fabric-spec groups (asgdynamic's dtlSet)
-- ---------------------------------------------------------------------------
CREATE TABLE gbl_business_document_line_groups (
    id                     BIGSERIAL     PRIMARY KEY,
    organization_id        BIGINT        NOT NULL,
    document_id            BIGINT        NOT NULL,
    group_no               INTEGER       NOT NULL DEFAULT 0,
    item_id                BIGINT,
    uom_id                 BIGINT,

    -- FabricSpec (embedded)
    construction           VARCHAR(60),
    declared_construction  VARCHAR(60),
    weave_type              VARCHAR(60),
    weave_style             VARCHAR(60),
    fabric_type             VARCHAR(60),
    finish_type              VARCHAR(60),
    composition              VARCHAR(120),
    declared_composition     VARCHAR(120),

    warp_count_1            VARCHAR(20),
    warp_count_2            VARCHAR(20),
    warp_count_3            VARCHAR(20),
    warp_count_ratio_1      NUMERIC(8,3),
    warp_count_ratio_2      NUMERIC(8,3),
    warp_count_ratio_3      NUMERIC(8,3),
    weft_count_1            VARCHAR(20),
    weft_count_2            VARCHAR(20),
    weft_count_3            VARCHAR(20),
    weft_count_ratio_1      NUMERIC(8,3),
    weft_count_ratio_2      NUMERIC(8,3),
    weft_count_ratio_3      NUMERIC(8,3),

    epi                     NUMERIC(10,2),
    ppi                     NUMERIC(10,2),

    shrinkage_warp          VARCHAR(20),
    shrinkage_weft          VARCHAR(20),
    shrinkage_mechanical    VARCHAR(20),

    gsm                     NUMERIC(12,4),
    gsm_before_wash         NUMERIC(12,4),
    gsm_after_wash          NUMERIC(12,4),
    finish_width            NUMERIC(12,4),
    cuttable_width          NUMERIC(12,4),

    light_source            VARCHAR(30),
    selvedge                VARCHAR(40),
    wash_type                VARCHAR(60),
    wash_instruction         VARCHAR(200),
    end_use                  VARCHAR(60),
    dispo_reference          VARCHAR(60),
    costing_code             VARCHAR(40),

    version                BIGINT,
    created_by             VARCHAR(100),
    created_at             TIMESTAMP,
    updated_by             VARCHAR(100),
    updated_at             TIMESTAMP,

    CONSTRAINT fk_gbdlg_document FOREIGN KEY (document_id)
        REFERENCES gbl_business_documents (id) ON DELETE CASCADE
);

CREATE INDEX ix_gbdlg_document ON gbl_business_document_line_groups (document_id);
CREATE INDEX ix_gbdlg_item     ON gbl_business_document_line_groups (item_id);
CREATE INDEX ix_gbdlg_org      ON gbl_business_document_line_groups (organization_id);
CREATE INDEX ix_gbdlg_costing_code ON gbl_business_document_line_groups (costing_code)
    WHERE costing_code IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Colour lines (asgdynamic's dtlLine) — quantity, price, fulfilment, and the reference
-- fields confirmed genuinely per-colour.
-- ---------------------------------------------------------------------------
CREATE TABLE gbl_business_document_color_lines (
    id                     BIGSERIAL     PRIMARY KEY,
    organization_id        BIGINT        NOT NULL,
    line_group_id          BIGINT        NOT NULL,
    color_line_no          INTEGER       NOT NULL DEFAULT 0,

    source_color_line_id   BIGINT,

    color_code             VARCHAR(40),
    color_name             VARCHAR(120),
    fabrics_style          VARCHAR(80),
    color_reference        VARCHAR(120),
    strike_off_reference   VARCHAR(120),
    lab_dip_reference      VARCHAR(120),
    loom_reference         VARCHAR(60),
    color_specification    VARCHAR(200),
    file_path              VARCHAR(300),

    quantity               NUMERIC(20,6) NOT NULL DEFAULT 0,
    rate                   NUMERIC(20,6) NOT NULL DEFAULT 0,
    price_in_meter         NUMERIC(20,6),
    fulfilled_quantity     NUMERIC(20,6) NOT NULL DEFAULT 0,
    line_amount            NUMERIC(20,6) NOT NULL DEFAULT 0,
    remarks                VARCHAR(500),

    version                BIGINT,
    created_by             VARCHAR(100),
    created_at             TIMESTAMP,
    updated_by             VARCHAR(100),
    updated_at             TIMESTAMP,

    CONSTRAINT fk_gbdcl_line_group FOREIGN KEY (line_group_id)
        REFERENCES gbl_business_document_line_groups (id) ON DELETE CASCADE,
    -- Self-referential: RESTRICT so a colour line that has been drawn against by a
    -- downstream document cannot be deleted out from under it — same reasoning as
    -- fk_gbdl_source_line on the table this replaces.
    CONSTRAINT fk_gbdcl_source_color_line FOREIGN KEY (source_color_line_id)
        REFERENCES gbl_business_document_color_lines (id) ON DELETE RESTRICT,

    CONSTRAINT ck_gbdcl_quantity   CHECK (quantity >= 0),
    CONSTRAINT ck_gbdcl_rate       CHECK (rate >= 0),
    CONSTRAINT ck_gbdcl_fulfilment CHECK (fulfilled_quantity >= 0 AND fulfilled_quantity <= quantity)
);

CREATE INDEX ix_gbdcl_line_group ON gbl_business_document_color_lines (line_group_id);
CREATE INDEX ix_gbdcl_org        ON gbl_business_document_color_lines (organization_id);
CREATE INDEX ix_gbdcl_source_color_line ON gbl_business_document_color_lines (source_color_line_id)
    WHERE source_color_line_id IS NOT NULL;
CREATE INDEX ix_gbdcl_outstanding ON gbl_business_document_color_lines (line_group_id)
    WHERE fulfilled_quantity < quantity;
