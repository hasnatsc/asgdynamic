-- Party maintenance screen: its screen grants, and indexes that keep the directory search fast
-- as it grows.

-- ---------------------------------------------------------------------------------------------
-- Grants for the new PARTY screen, on the two working roles V8 set up
-- ---------------------------------------------------------------------------------------------

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, 'PARTY', TRUE, TRUE, TRUE, TRUE, FALSE, 0, 'seed', now()
FROM sec_fabric_roles r
WHERE r.name = 'ROLE_FABRIC_OPERATION'
ON CONFLICT (role_id, screen_code) DO NOTHING;

-- Approvers see who a document names; they do not maintain the directory.
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT r.id, 'PARTY', TRUE, FALSE, FALSE, FALSE, FALSE, 0, 'seed', now()
FROM sec_fabric_roles r
WHERE r.name = 'ROLE_DOCUMENT_APPROVER'
ON CONFLICT (role_id, screen_code) DO NOTHING;

-- ---------------------------------------------------------------------------------------------
-- Directory search
-- ---------------------------------------------------------------------------------------------

-- The default listing: live parties of one organization, in code order.
CREATE INDEX ix_pty_party_directory ON pty_parties (organization_id, code) WHERE NOT deleted;

-- Search is "contains", on code, name and the legacy role codes: lower(x) LIKE '%term%'. A
-- b-tree cannot serve a leading wildcard; a trigram GIN index can. pg_trgm ships with
-- PostgreSQL but creating an extension needs privileges an application role may not have, so
-- this is attempted and skipped with a notice rather than failing the migration - search still
-- works without it, it just scans.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
    CREATE INDEX ix_pty_party_name_trgm ON pty_parties USING gin (lower(name) gin_trgm_ops);
    CREATE INDEX ix_pty_party_code_trgm ON pty_parties USING gin (lower(code) gin_trgm_ops);
    CREATE INDEX ix_pty_role_code_trgm  ON pty_party_roles USING gin (lower(role_code) gin_trgm_ops);
EXCEPTION
    WHEN insufficient_privilege OR undefined_file THEN
        RAISE NOTICE 'pg_trgm not available (%); party search will run without trigram indexes', SQLERRM;
END $$;
