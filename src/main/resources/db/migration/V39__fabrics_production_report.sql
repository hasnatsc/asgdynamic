-- =============================================================================================
-- The Fabrics production report (Analytics & reports -> Fabrics production report): read-only,
-- so whoever may view the Production dashboard may view it and print it.
-- =============================================================================================

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'PROD_REPORT', TRUE, FALSE, FALSE, FALSE, FALSE, 0, 'V39', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'PROD_DASHBOARD' AND g.can_view
ON CONFLICT (role_id, screen_code) DO NOTHING;
