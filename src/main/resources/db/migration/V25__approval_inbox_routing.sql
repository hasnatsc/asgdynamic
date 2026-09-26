-- Centralised approval inbox.
--
-- Until now the inbox loaded every pending request in the business unit and, one by one, loaded
-- its document and its matrix to ask "is this for me?". That is fine for a handful and hopeless for
-- the volume Booking, BPO and the work orders produce. Each request now carries WHO its current
-- level waits for - the role or the named user, copied from the matrix whenever the request moves -
-- so "waiting for me" is a single indexed, paged query.
--
-- Both columns are null under the default rule (no matrix: anyone with the screen's Approve verb)
-- and once the request is settled. The matrix stays the authority; these are its index.

ALTER TABLE apr_requests
    ADD COLUMN current_role_id BIGINT,
    ADD COLUMN current_user_id BIGINT;

-- Backfill every request already in flight: the current_level-th of the levels whose amount band
-- covers the request's amount, in sequence order - exactly ApprovalMatrix.levelFor().
UPDATE apr_requests r
SET current_role_id = l.role_id,
    current_user_id = l.user_id
FROM (
    SELECT q.id AS request_id, lv.role_id, lv.user_id,
           ROW_NUMBER() OVER (PARTITION BY q.id ORDER BY lv.sequence) AS position
    FROM apr_requests q
    JOIN apr_matrix_levels lv ON lv.matrix_id = q.matrix_id
    WHERE q.pending
      AND (q.amount IS NULL
           OR ((lv.min_amount IS NULL OR q.amount >= lv.min_amount)
               AND (lv.max_amount IS NULL OR q.amount <= lv.max_amount)))
) l
WHERE l.request_id = r.id
  AND l.position = r.current_level;

CREATE INDEX ix_apr_request_current_user ON apr_requests (organization_id, business_unit_id, current_user_id) WHERE pending;
CREATE INDEX ix_apr_request_current_role ON apr_requests (organization_id, business_unit_id, current_role_id) WHERE pending;
