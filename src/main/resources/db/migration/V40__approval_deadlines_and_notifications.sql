-- Approval deadlines, and the notification centre that tells people about them.
--
--   apr_matrix_levels   each level may carry a time limit, and what happens when it runs out:
--                       REMIND (tell everyone it is overdue and keep waiting), ESCALATE (hand the
--                       level to another role or person), or AUTO_APPROVE / AUTO_RETURN /
--                       AUTO_REJECT (the system decides the level itself, with the reason recorded).
--   apr_requests        the clock for the level it is at: when the level started, when a reminder
--                       is due (at three quarters of the limit), when it runs out, and whether the
--                       reminder and the timeout have been dealt with. Reset every time the request
--                       moves to another level. escalated says the route now names the escalation
--                       target rather than the matrix's approver.
--   ntf_notifications   one row per person per event - what the bell in the top bar shows.
--   ntf_messages        person-to-person messages, optionally about a document.

ALTER TABLE apr_matrix_levels
    ADD COLUMN time_limit_minutes INTEGER,
    ADD COLUMN timeout_action     VARCHAR(20) NOT NULL DEFAULT 'REMIND',
    ADD COLUMN escalate_role_id   BIGINT,
    ADD COLUMN escalate_user_id   BIGINT,
    ADD CONSTRAINT fk_apr_level_escalate_role FOREIGN KEY (escalate_role_id) REFERENCES sec_fabric_roles (id),
    ADD CONSTRAINT fk_apr_level_escalate_user FOREIGN KEY (escalate_user_id) REFERENCES sec_fabric_users (id),
    ADD CONSTRAINT ck_apr_level_time_limit CHECK (time_limit_minutes IS NULL OR time_limit_minutes > 0),
    ADD CONSTRAINT ck_apr_level_timeout_action
        CHECK (timeout_action IN ('REMIND', 'ESCALATE', 'AUTO_APPROVE', 'AUTO_RETURN', 'AUTO_REJECT')),
    -- escalating names exactly one target; nothing else names any
    ADD CONSTRAINT ck_apr_level_escalation CHECK (
        (timeout_action = 'ESCALATE' AND (escalate_role_id IS NULL) <> (escalate_user_id IS NULL))
        OR (timeout_action <> 'ESCALATE' AND escalate_role_id IS NULL AND escalate_user_id IS NULL));

ALTER TABLE apr_requests
    ADD COLUMN level_started_at TIMESTAMP,
    ADD COLUMN level_remind_at  TIMESTAMP,
    ADD COLUMN level_due_at     TIMESTAMP,
    ADD COLUMN level_reminded   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN level_timed_out  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN escalated        BOOLEAN NOT NULL DEFAULT FALSE;

-- Requests already in flight started their level when they last moved; none has a limit yet.
UPDATE apr_requests SET level_started_at = COALESCE(updated_at, created_at) WHERE pending;

-- What the deadline job asks every minute: pending, with a clock still running.
CREATE INDEX ix_apr_request_remind ON apr_requests (level_remind_at) WHERE pending AND NOT level_reminded;
CREATE INDEX ix_apr_request_due    ON apr_requests (level_due_at)    WHERE pending AND NOT level_timed_out;

CREATE TABLE ntf_notifications (
    id                BIGSERIAL     PRIMARY KEY,
    organization_id   BIGINT        NOT NULL,
    recipient_user_id BIGINT        NOT NULL,
    kind              VARCHAR(30)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    body              VARCHAR(1000),
    link              VARCHAR(300),
    document_id       BIGINT,
    document_label    VARCHAR(120),
    -- who caused it, when a person did: the panel offers to message them
    actor_user_id     BIGINT,
    read_at           TIMESTAMP,
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,

    CONSTRAINT fk_ntf_recipient FOREIGN KEY (recipient_user_id) REFERENCES sec_fabric_users (id) ON DELETE CASCADE
);

CREATE INDEX ix_ntf_recipient ON ntf_notifications (recipient_user_id, id DESC);
CREATE INDEX ix_ntf_unread    ON ntf_notifications (recipient_user_id) WHERE read_at IS NULL;

CREATE TABLE ntf_messages (
    id                BIGSERIAL     PRIMARY KEY,
    organization_id   BIGINT        NOT NULL,
    sender_user_id    BIGINT        NOT NULL,
    recipient_user_id BIGINT        NOT NULL,
    body              VARCHAR(2000) NOT NULL,
    document_id       BIGINT,
    document_label    VARCHAR(120),
    link              VARCHAR(300),
    read_at           TIMESTAMP,
    version           BIGINT,
    created_by        VARCHAR(100),
    created_at        TIMESTAMP,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMP,

    CONSTRAINT fk_msg_sender    FOREIGN KEY (sender_user_id)    REFERENCES sec_fabric_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_recipient FOREIGN KEY (recipient_user_id) REFERENCES sec_fabric_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_msg_not_self  CHECK (sender_user_id <> recipient_user_id)
);

CREATE INDEX ix_msg_recipient ON ntf_messages (recipient_user_id, id DESC);
CREATE INDEX ix_msg_sender    ON ntf_messages (sender_user_id, id DESC);
CREATE INDEX ix_msg_unread    ON ntf_messages (recipient_user_id) WHERE read_at IS NULL;
