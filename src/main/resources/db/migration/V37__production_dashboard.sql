-- =============================================================================================
-- The production dashboard (Analytics & reports -> Production dashboard): read-only, so whoever
-- may view the Production board may view it.
-- =============================================================================================

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'PROD_DASHBOARD', TRUE, FALSE, FALSE, FALSE, FALSE, 0, 'V37', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'PROD_BOARD' AND g.can_view
ON CONFLICT (role_id, screen_code) DO NOTHING;
