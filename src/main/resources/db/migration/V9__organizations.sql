-- The tenant/business-unit/warehouse tables every OrgScoped row's organizationId (and
-- FabricUser's businessUnitId/warehouseId) has pointed at as a bare number until now. See
-- OrgScoped's javadoc for why those stay plain scalar ids rather than becoming @ManyToOne
-- relations — this migration only makes the ids resolve to something real.
--
-- APPLY THIS BY HAND — same convention as every other migration here.

CREATE TABLE org_organizations (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT,
    created_by  VARCHAR(100),
    created_at  TIMESTAMP,
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMP,

    CONSTRAINT uk_org_code UNIQUE (code)
);

CREATE TABLE org_business_units (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    code             VARCHAR(4)   NOT NULL,
    name             VARCHAR(150) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,

    CONSTRAINT uk_bu_org_code UNIQUE (organization_id, code)
);

CREATE INDEX ix_bu_org ON org_business_units (organization_id);

CREATE TABLE org_warehouses (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    business_unit_id BIGINT,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(150) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,

    CONSTRAINT uk_warehouse_org_code UNIQUE (organization_id, code)
);

CREATE INDEX ix_warehouse_org ON org_warehouses (organization_id);

-- Seed: matches DevUserSeeder's existing hardcoded literals exactly (organizationId = 1L,
-- businessUnitCode = "AF") — this file gives those two numbers/strings a real row to resolve
-- to; DevUserSeeder itself needs no change.

INSERT INTO org_organizations (code, name, active, created_by, created_at)
SELECT 'ASG', 'ASG Dynamic', TRUE, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM org_organizations WHERE code = 'ASG');

INSERT INTO org_business_units (organization_id, code, name, active, created_by, created_at)
SELECT (SELECT id FROM org_organizations WHERE code = 'ASG'), 'AF', 'Amanatshah Fabrics', TRUE, 'seed', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM org_business_units WHERE code = 'AF'
      AND organization_id = (SELECT id FROM org_organizations WHERE code = 'ASG')
);
