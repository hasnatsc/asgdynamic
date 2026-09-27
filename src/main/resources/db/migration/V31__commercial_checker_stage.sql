-- The Commercial family's maker -> checker -> approver triad.
--
-- The legacy system signed proforma invoices, letters of credit and commercial invoices in three
-- stages, each its own role (business-logic-capture/role-model-and-data-analysis.md):
--
--   Proforma invoice     ROLE_PI_MAKER  ROLE_PI_CHECKER  ROLE_PI_APPROVAL
--   Letter of credit     ROLE_LC_MAKER  ROLE_LC_CHECKER  ROLE_LC_APPROVAL
--   Commercial invoice   ROLE_CI_MAKER  ROLE_CI_CHECKER  ROLE_CI_APPROVAL
--
-- V11 seeded those nine names as empty shells. This adds the CHECK verb the middle stage needs and
-- gives each shell the grant its name has always meant, on the new PI, LC and CI screens (export,
-- import and back-to-back instruments share their family's screen, as they shared its roles).
-- With no approval matrix, ApprovalService routes these types to CHECK first and APPROVE second;
-- whoever checked a document may not also approve it.
--
-- APPLY THIS BY HAND — same convention as every other migration here.

ALTER TABLE sec_fabric_role_screen_grants
    ADD COLUMN can_check BOOLEAN NOT NULL DEFAULT FALSE;

-- Screen.supports(Verb): only the Commercial screens have a checker stage. RoleScreenGrant drops a
-- CHECK ticked anywhere else; the database refuses one written past it.
ALTER TABLE sec_fabric_role_screen_grants
    ADD CONSTRAINT ck_fab_grant_check_commercial
    CHECK (NOT can_check OR screen_code IN ('PI', 'LC', 'CI'));

-- Makers raise, correct and discard; checkers check; approvers approve. Each may view.
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_check, can_approve,
     version, created_by, created_at)
SELECT r.id, g.screen_code, TRUE, g.maker, g.maker, g.maker, g.checker, g.approver, 0, 'seed', now()
FROM sec_fabric_roles r
JOIN (VALUES
    ('ROLE_PI_MAKER',    'PI', TRUE,  FALSE, FALSE),
    ('ROLE_PI_CHECKER',  'PI', FALSE, TRUE,  FALSE),
    ('ROLE_PI_APPROVAL', 'PI', FALSE, FALSE, TRUE),
    ('ROLE_LC_MAKER',    'LC', TRUE,  FALSE, FALSE),
    ('ROLE_LC_CHECKER',  'LC', FALSE, TRUE,  FALSE),
    ('ROLE_LC_APPROVAL', 'LC', FALSE, FALSE, TRUE),
    ('ROLE_CI_MAKER',    'CI', TRUE,  FALSE, FALSE),
    ('ROLE_CI_CHECKER',  'CI', FALSE, TRUE,  FALSE),
    ('ROLE_CI_APPROVAL', 'CI', FALSE, FALSE, TRUE)
) AS g(role_name, screen_code, maker, checker, approver) ON g.role_name = r.name
ON CONFLICT (role_id, screen_code) DO NOTHING;
