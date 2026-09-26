-- Replaces the flat sec_fabric_user_authorities collection with an admin-editable
-- Permission -> Role -> User model. See FabricUser's javadoc for why this does not change how
-- any route is protected: @PreAuthorize stays the enforcement, unchanged. Permission.name is
-- the exact GrantedAuthority string every existing @PreAuthorize/hasRole(...) literal already
-- checks (audited against the whole codebase, not guessed) — a role bundles existing
-- permissions, a user holds roles, and FabricUserPrincipal derives the same flat authority set
-- @PreAuthorize always saw, just sourced from these tables instead of a hand-maintained list.
--
-- APPLY THIS BY HAND — same as every other migration here (spring.flyway.enabled reads
-- FLYWAY_ENABLED, off by default; see application.properties).
--
-- Existing local databases already have 'admin'/'approver' rows seeded under the old scheme.
-- DevUserSeeder's idempotency check (existsByUsernameIgnoreCase) means it will NOT touch them,
-- so after this migration those two accounts have zero roles until either assigned by hand via
-- the new Roles screen, or the two rows are deleted so the rewritten DevUserSeeder recreates
-- them with roles on next boot. Dev bootstrap data only (app.seed-dev-user-gated) — not
-- production data.

CREATE TABLE sec_fabric_permissions (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    module      VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT,
    created_by  VARCHAR(100),
    created_at  TIMESTAMP,
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMP,

    CONSTRAINT uk_fab_permission_name UNIQUE (name)
);

CREATE TABLE sec_fabric_roles (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT,
    created_by  VARCHAR(100),
    created_at  TIMESTAMP,
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMP,

    CONSTRAINT uk_fab_role_name UNIQUE (name)
);

CREATE TABLE sec_fabric_role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,

    CONSTRAINT fk_fab_role_perm_role FOREIGN KEY (role_id)
        REFERENCES sec_fabric_roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_fab_role_perm_permission FOREIGN KEY (permission_id)
        REFERENCES sec_fabric_permissions (id) ON DELETE CASCADE,
    CONSTRAINT pk_fab_role_permission PRIMARY KEY (role_id, permission_id)
);
-- The primary key above already covers role_id as its leading column, so "every permission for
-- this role" is index-served without a second index.

CREATE TABLE sec_fabric_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    CONSTRAINT fk_fab_user_role_user FOREIGN KEY (user_id)
        REFERENCES sec_fabric_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_fab_user_role_role FOREIGN KEY (role_id)
        REFERENCES sec_fabric_roles (id) ON DELETE CASCADE,
    CONSTRAINT pk_fab_user_role PRIMARY KEY (user_id, role_id)
);

DROP TABLE sec_fabric_user_authorities;

-- ---------------------------------------------------------------------------------------------
-- Seed: one permission row per GrantedAuthority string an @PreAuthorize/hasRole(...) literal
-- checks today (audited across every controller and DevUserSeeder — nothing speculative added).
-- ---------------------------------------------------------------------------------------------

INSERT INTO sec_fabric_permissions (name, module, description, active, created_by, created_at) VALUES
    ('ROLE_FABRIC_SETUP', 'FABRIC_SETUP', 'Manage fabric reference lists (weave type, blend, etc.)', TRUE, 'seed', now()),
    ('ROLE_BOOKING_VIEW',  'BOOKING',     'View bookings',                                            TRUE, 'seed', now()),
    ('ROLE_BOOKING_MAKER', 'BOOKING',     'Create, revise and delete bookings',                        TRUE, 'seed', now()),
    ('ROLE_SALES',         'SALES',       'Cross-module sales access (bookings, delivery orders, fabrics delivery, PI requests)', TRUE, 'seed', now()),
    ('ROLE_BPO_VIEW',      'BPO',         'View bulk production orders',                               TRUE, 'seed', now()),
    ('ROLE_BPO_MAKER',     'BPO',         'Create, revise and delete bulk production orders',          TRUE, 'seed', now()),
    ('ROLE_PRODUCTION',    'PRODUCTION',  'Cross-module production access (BPO, work orders, greige receive)', TRUE, 'seed', now()),
    ('ROLE_RPI_VIEW',      'RPI',         'View request-for-PI documents',                             TRUE, 'seed', now()),
    ('ROLE_RPI_MAKER',     'RPI',         'Create, revise and delete request-for-PI documents',        TRUE, 'seed', now()),
    ('ROLE_WWO_VIEW',      'WWO',         'View weaving work orders',                                  TRUE, 'seed', now()),
    ('ROLE_WWO_MAKER',     'WWO',         'Create and delete weaving work orders',                     TRUE, 'seed', now()),
    ('ROLE_PWO_VIEW',      'PWO',         'View processing work orders',                               TRUE, 'seed', now()),
    ('ROLE_PWO_MAKER',     'PWO',         'Create and delete processing work orders',                  TRUE, 'seed', now()),
    ('ROLE_GR_VIEW',       'GR',          'View greige receive documents',                             TRUE, 'seed', now()),
    ('ROLE_GR_MAKER',      'GR',          'Create and delete greige receive documents',                TRUE, 'seed', now()),
    ('ROLE_DO_VIEW',       'DO',          'View delivery orders',                                      TRUE, 'seed', now()),
    ('ROLE_DO_MAKER',      'DO',          'Create and delete delivery orders',                         TRUE, 'seed', now()),
    ('ROLE_FD_VIEW',       'FD',          'View fabrics delivery documents',                           TRUE, 'seed', now()),
    ('ROLE_FD_MAKER',      'FD',          'Create and delete fabrics delivery documents',              TRUE, 'seed', now()),
    ('ROLE_APPROVAL',      'APPROVAL',    'Approve or reject submitted documents (role resolved per document type — see ApprovalService)', TRUE, 'seed', now()),
    ('ROLE_SECURITY_ADMIN','SECURITY',    'Manage permissions, roles and users',                       TRUE, 'seed', now());

-- ---------------------------------------------------------------------------------------------
-- Seed: two roles reproducing today's exact effective access (DevUserSeeder's old seedMaker()/
-- seedApprover() grant lists), plus ROLE_SECURITY_ADMIN on the maker role so the existing
-- 'admin' bootstrap account can reach the screens this migration adds.
-- ---------------------------------------------------------------------------------------------

INSERT INTO sec_fabric_roles (name, description, active, created_by, created_at) VALUES
    ('ROLE_FABRIC_OPERATION', 'Create and submit documents across every fabric module built so far.', TRUE, 'seed', now()),
    ('ROLE_DOCUMENT_APPROVER', 'Approve or reject submitted documents; view-only otherwise.',            TRUE, 'seed', now());

INSERT INTO sec_fabric_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM sec_fabric_roles r, sec_fabric_permissions p
WHERE r.name = 'ROLE_FABRIC_OPERATION'
  AND p.name IN (
    'ROLE_FABRIC_SETUP', 'ROLE_BOOKING_VIEW', 'ROLE_BOOKING_MAKER', 'ROLE_SALES',
    'ROLE_BPO_VIEW', 'ROLE_BPO_MAKER', 'ROLE_PRODUCTION', 'ROLE_RPI_VIEW', 'ROLE_RPI_MAKER',
    'ROLE_WWO_VIEW', 'ROLE_WWO_MAKER', 'ROLE_PWO_VIEW', 'ROLE_PWO_MAKER', 'ROLE_GR_VIEW',
    'ROLE_GR_MAKER', 'ROLE_DO_VIEW', 'ROLE_DO_MAKER', 'ROLE_FD_VIEW', 'ROLE_FD_MAKER',
    'ROLE_SECURITY_ADMIN'
  );

INSERT INTO sec_fabric_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM sec_fabric_roles r, sec_fabric_permissions p
WHERE r.name = 'ROLE_DOCUMENT_APPROVER'
  AND p.name IN (
    'ROLE_APPROVAL', 'ROLE_BOOKING_VIEW', 'ROLE_BPO_VIEW', 'ROLE_RPI_VIEW', 'ROLE_WWO_VIEW',
    'ROLE_PWO_VIEW', 'ROLE_GR_VIEW', 'ROLE_DO_VIEW', 'ROLE_FD_VIEW'
  );
