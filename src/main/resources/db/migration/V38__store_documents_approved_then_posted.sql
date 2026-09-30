-- =============================================================================================
-- Store documents are approved, then posted. Greige receive and issue, finished receive, delivery
-- order, fabrics delivery, store requisition, material issue, direct receive, transfer request,
-- issue and receive, stock adjustment and fabric transfer issue and receive are created, edited
-- and deleted as drafts, submitted through the approval matrix, left READY_TO_POST by the last
-- approver, and take effect only when posted (DocumentType.isPostedAfterApproval()).
--
-- No schema change: status is a VARCHAR(30) with no CHECK, and every document already approved
-- under the old rules is APPROVED - which, for these types, now means posted.
--
-- Material issue, direct receive, transfer issue and receive, and the fabric transfers were posted
-- straight from draft, so no role was ever given APPROVE on them. A type with no matrix is approved
-- by whoever holds its screen's APPROVE verb; without this, nobody could sign them. Whoever approves
-- the neighbouring document now approves these too:
--   Store requisition (SR)  -> Material issue (MI), Direct receive (MR)
--   Transfer request (ST)   -> Transfer issue (TI), Transfer receive (TRC)
--   Greige receive (GR)     -> Fabric transfer issue (FTI), Fabric transfer receive (FTR)
-- =============================================================================================

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, s.code, TRUE, FALSE, FALSE, FALSE, TRUE, 0, 'V38', now()
FROM sec_fabric_role_screen_grants g
JOIN (VALUES ('SR', 'MI'), ('SR', 'MR'), ('ST', 'TI'), ('ST', 'TRC'), ('GR', 'FTI'), ('GR', 'FTR'))
    AS s(source, code) ON g.screen_code = s.source
WHERE g.can_approve
ON CONFLICT (role_id, screen_code) DO UPDATE
    SET can_approve = TRUE, can_view = TRUE,
        version = COALESCE(sec_fabric_role_screen_grants.version, 0) + 1,
        updated_by = 'V38', updated_at = now();
