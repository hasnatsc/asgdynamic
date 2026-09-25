-- Marketing teams become a maintained master (ADM-4), with team-wise booking approval.
--
-- asfl-erp's sales.marketing_team carries a leader, a booking target and remarks; this adds
-- the same three columns to the team V12 seeded. Membership is NOT a new table here: a user's
-- team is already their MARKETING_TEAM data-scope grant (sec_fabric_data_scopes), effective-
-- dated and limited to one open grant per user by uk_fab_scope_one_team. The team screen
-- writes that same grant, so "which team is this person in" has one answer.

ALTER TABLE org_marketing_teams
    ADD COLUMN leader_user_id BIGINT,
    ADD COLUMN booking_target NUMERIC(18, 2),
    ADD COLUMN remarks        VARCHAR(500);

ALTER TABLE org_marketing_teams
    ADD CONSTRAINT fk_marketing_team_leader FOREIGN KEY (leader_user_id) REFERENCES sec_fabric_users (id);

-- ---------------------------------------------------------------------------------------------
-- Team approvers — asfl-erp V27's per-team approval matrix, reduced to what this system routes.
--
-- A team with approvers: its documents are approved or rejected by those people only.
-- A team with none: the business-wide rule applies (anyone holding the screen's APPROVE verb),
-- exactly as before this migration. So a team without its own approvers is not a gap - it falls
-- back - and nothing already in flight changes hands when this is applied.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE org_marketing_team_approvers (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    team_id          BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_by       VARCHAR(100),
    updated_at       TIMESTAMP,

    CONSTRAINT fk_mt_approver_team FOREIGN KEY (team_id) REFERENCES org_marketing_teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_mt_approver_user FOREIGN KEY (user_id) REFERENCES sec_fabric_users (id),
    CONSTRAINT uk_mt_approver UNIQUE (team_id, user_id)
);

CREATE INDEX ix_mt_approver_user ON org_marketing_team_approvers (user_id);

-- ---------------------------------------------------------------------------------------------
-- The new screen, granted as Security administration is: team membership IS a user's data
-- scope, so whoever may grant scopes maintains the teams.
-- ---------------------------------------------------------------------------------------------
INSERT INTO sec_fabric_role_screen_grants
    (role_id, screen_code, can_view, can_create, can_amend, can_delete, can_approve, version, created_by, created_at)
SELECT g.role_id, 'MARKETING_TEAM', g.can_view, g.can_create, g.can_amend, g.can_delete, FALSE, 0, 'seed', now()
FROM sec_fabric_role_screen_grants g
WHERE g.screen_code = 'SECURITY_ADMIN'
ON CONFLICT (role_id, screen_code) DO NOTHING;
