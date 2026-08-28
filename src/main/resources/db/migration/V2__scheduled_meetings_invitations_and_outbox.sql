ALTER TABLE meetings RENAME COLUMN start_time TO planned_start_time;
ALTER TABLE meetings RENAME COLUMN end_time TO planned_end_time;
UPDATE meetings SET planned_start_time = COALESCE(planned_start_time, created_at, CURRENT_TIMESTAMP);
UPDATE meetings SET planned_end_time = COALESCE(planned_end_time, planned_start_time + INTERVAL '2 hours');
ALTER TABLE meetings ALTER COLUMN planned_start_time SET NOT NULL;
ALTER TABLE meetings ALTER COLUMN planned_end_time SET NOT NULL;
ALTER TABLE meetings ADD COLUMN started_at TIMESTAMPTZ;
ALTER TABLE meetings ADD COLUMN ended_at TIMESTAMPTZ;
ALTER TABLE meetings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE meetings SET status = 'IN_PROGRESS' WHERE status = 'ONGOING';

CREATE INDEX idx_meetings_host_planned_start ON meetings(host_id, planned_start_time);
CREATE INDEX idx_meetings_status_planned_start ON meetings(status, planned_start_time);

CREATE TABLE meeting_invitations (
    id VARCHAR(36) PRIMARY KEY,
    meeting_id VARCHAR(255) NOT NULL REFERENCES meetings(id),
    invitee_email VARCHAR(320) NOT NULL,
    invitee_user_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_meeting_invitee_email UNIQUE(meeting_id, invitee_email)
);
CREATE INDEX idx_invitations_invitee_status ON meeting_invitations(invitee_email, status);

CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_outbox_claimable ON outbox_events(status, next_retry_at);
