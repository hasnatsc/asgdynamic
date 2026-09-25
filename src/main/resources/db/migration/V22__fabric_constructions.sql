-- The fabric quality master: the construction a booking line will reference instead of
-- re-keying some sixty spec columns (warp/weft count 1-3 and ratios, EPI, PPI, widths, GSM,
-- weave, composition ...) on every order. Colours stay on the document's colour lines.
--
--   fab_constructions        one quality: weave, density, widths, GSM
--   fab_construction_yarns   its warp and weft yarns, up to three each, in loom order, with ratios
--   fab_construction_fibres  its composition as fibre + percentage rows (they total 100), so
--                            "70% Viscose 30% Linen" can be searched and reported, not parsed
--
-- Yarn rows reference the yarn masters (count required; type and blend optional) - the same
-- attributes that identify a YARN item, so planning can resolve a row to stock later.

CREATE TABLE fab_constructions (
    id               BIGSERIAL     PRIMARY KEY,
    organization_id  BIGINT        NOT NULL,
    code             VARCHAR(40)   NOT NULL,
    name             VARCHAR(200),
    weave_type_id    BIGINT        REFERENCES fab_attributes (id),
    weave_style_id   BIGINT        REFERENCES fab_attributes (id),
    finish_type_id   BIGINT        REFERENCES fab_attributes (id),
    epi              NUMERIC(10,2),
    ppi              NUMERIC(10,2),
    reed_count       NUMERIC(10,2),
    greige_width     NUMERIC(10,2),
    finish_width     NUMERIC(10,2),
    cuttable_width   NUMERIC(10,2),
    gsm              NUMERIC(10,2),
    remarks          VARCHAR(1000),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,
    CONSTRAINT uk_fab_construction_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_fab_construction_positive CHECK (
        (epi IS NULL OR epi > 0) AND (ppi IS NULL OR ppi > 0) AND (reed_count IS NULL OR reed_count > 0)
        AND (greige_width IS NULL OR greige_width > 0) AND (finish_width IS NULL OR finish_width > 0)
        AND (cuttable_width IS NULL OR cuttable_width > 0) AND (gsm IS NULL OR gsm > 0)),
    CONSTRAINT ck_fab_construction_cuttable CHECK (
        cuttable_width IS NULL OR finish_width IS NULL OR cuttable_width <= finish_width)
);
CREATE INDEX ix_fab_construction_directory ON fab_constructions (organization_id, code) WHERE NOT deleted;

CREATE TABLE fab_construction_yarns (
    id               BIGSERIAL     PRIMARY KEY,
    organization_id  BIGINT        NOT NULL,
    construction_id  BIGINT        NOT NULL REFERENCES fab_constructions (id) ON DELETE CASCADE,
    direction        VARCHAR(10)   NOT NULL,
    sequence         INTEGER       NOT NULL,
    yarn_count_id    BIGINT        NOT NULL REFERENCES yrn_counts (id),
    yarn_type_id     BIGINT        REFERENCES yrn_types (id),
    yarn_blend_id    BIGINT        REFERENCES yrn_blends (id),
    ratio            NUMERIC(8,3)  NOT NULL DEFAULT 1,
    remarks          VARCHAR(300),
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,
    CONSTRAINT ck_fab_construction_yarn_direction CHECK (direction IN ('WARP', 'WEFT')),
    CONSTRAINT ck_fab_construction_yarn_sequence  CHECK (sequence BETWEEN 1 AND 3),
    CONSTRAINT ck_fab_construction_yarn_ratio     CHECK (ratio > 0),
    CONSTRAINT uk_fab_construction_yarn UNIQUE (construction_id, direction, sequence)
);

CREATE TABLE fab_construction_fibres (
    id               BIGSERIAL     PRIMARY KEY,
    organization_id  BIGINT        NOT NULL,
    construction_id  BIGINT        NOT NULL REFERENCES fab_constructions (id) ON DELETE CASCADE,
    sequence         INTEGER       NOT NULL,
    fiber_type       VARCHAR(30)   NOT NULL,
    percentage       NUMERIC(5,2)  NOT NULL,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,
    CONSTRAINT ck_fab_construction_fibre_pct CHECK (percentage > 0 AND percentage <= 100),
    CONSTRAINT uk_fab_construction_fibre UNIQUE (construction_id, fiber_type)
);

-- ---------------------------------------------------------------------------------------------
-- The new screen, granted as Fabric setup is: whoever maintains the fabric reference lists
-- maintains the qualities built from them.
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'QUALITY', g.can_view, g.can_create, g.can_amend, g.can_delete, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'FABRIC_SETUP'
ON CONFLICT (role_id, screen_code) DO NOTHING;
