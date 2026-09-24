-- Replaces the flat Permission catalog (V7) with a verb-per-screen model, ported from
-- asfl-erp's admin module's RolePermission: five separately-grantable verbs (VIEW, CREATE,
-- AMEND, DELETE, APPROVE) as five booleans on one (role, screen) row, instead of one flat
-- "MAKER" authority conflating create+amend+delete. See Role/RoleScreenGrant/Screen/Verb's
-- javadoc in the security package.
--
-- APPLY THIS BY HAND — Flyway is off here too (FLYWAY_ENABLED, default false).

DROP TABLE sec_fabric_role_permissions;
DROP TABLE sec_fabric_permissions;

CREATE TABLE sec_fabric_role_screen_grants (
    id          BIGSERIAL   PRIMARY KEY,
    role_id     BIGINT      NOT NULL,
    screen_code VARCHAR(20) NOT NULL,
    can_view    BOOLEAN     NOT NULL DEFAULT FALSE,
    can_create  BOOLEAN     NOT NULL DEFAULT FALSE,
    can_amend   BOOLEAN     NOT NULL DEFAULT FALSE,
    can_delete  BOOLEAN     NOT NULL DEFAULT FALSE,
    can_approve BOOLEAN     NOT NULL DEFAULT FALSE,
    version     BIGINT,
    created_by  VARCHAR(100),
    created_at  TIMESTAMP,
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMP,

    CONSTRAINT fk_fab_grant_role FOREIGN KEY (role_id)
        REFERENCES sec_fabric_roles (id) ON DELETE CASCADE,
    CONSTRAINT uk_fab_grant_role_screen UNIQUE (role_id, screen_code)
);
-- The unique constraint above already covers role_id as its leading column, so "every grant
-- for this role" is index-served without a second index.

-- ---------------------------------------------------------------------------------------------
-- Seed: reproduce today's effective access exactly (V7's two roles, same grant boundaries).
-- ---------------------------------------------------------------------------------------------

-- 'Fabric Operations' — full CRUD on every screen it used to hold ROLE_*_MAKER +
-- ROLE_FABRIC_SETUP + (new) the admin screens this migration adds SCREEN_SECURITY_ADMIN_* for.
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, created_by, created_at)
SELECT r.id, s.screen_code, TRUE, TRUE, TRUE, TRUE, FALSE, 'seed', now()
FROM sec_fabric_roles r,
     (VALUES ('BOOKING'), ('BPO'), ('RPI'), ('WWO'), ('PWO'), ('GR'), ('DO'), ('FD'),
             ('FABRIC_SETUP'), ('SECURITY_ADMIN')) AS s(screen_code)
WHERE r.name = 'Fabric Operations';

-- 'Document Approver' — view + approve on the 8 document screens (not FABRIC_SETUP or
-- SECURITY_ADMIN), matching the old single global ROLE_APPROVAL applied everywhere it held
-- ROLE_*_VIEW.
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, created_by, created_at)
SELECT r.id, s.screen_code, TRUE, FALSE, FALSE, FALSE, TRUE, 'seed', now()
FROM sec_fabric_roles r,
     (VALUES ('BOOKING'), ('BPO'), ('RPI'), ('WWO'), ('PWO'), ('GR'), ('DO'), ('FD')) AS s(screen_code)
WHERE r.name = 'Document Approver';
