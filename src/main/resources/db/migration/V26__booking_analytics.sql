-- Analytics & reports, starting with Booking analytics.
--
-- The screen is opened with VIEW on BOOKING_ANALYTICS; WHAT it shows is decided by who is looking
-- (BookingAnalyticsService / AnalyticsScope): a team member's own bookings, a supervisor's or
-- approver's teams, or every team for management. So the grant can safely go to everyone who
-- already works with bookings - every role that may view, raise or approve them.

INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'BOOKING_ANALYTICS', TRUE, FALSE, FALSE, FALSE, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'BOOKING'
  AND (g.can_view OR g.can_create OR g.can_approve)
ON CONFLICT (role_id, screen_code) DO NOTHING;

-- Every analytics query starts from "this unit's live bookings in a period".
CREATE INDEX IF NOT EXISTS ix_gbd_booking_analytics
    ON gbl_business_documents (organization_id, business_unit_id, document_type, document_date)
    WHERE deleted = false;

-- The approval figures join requests to the bookings in scope.
CREATE INDEX IF NOT EXISTS ix_apr_request_document_outcome
    ON apr_requests (document_id, outcome);
