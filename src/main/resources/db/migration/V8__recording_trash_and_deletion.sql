ALTER TABLE recordings
    ADD COLUMN deletion_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN trashed_at TIMESTAMPTZ,
    ADD COLUMN purge_after TIMESTAMPTZ,
    ADD COLUMN deletion_id VARCHAR(36),
    ADD COLUMN deletion_requested_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deletion_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN deletion_next_retry_at TIMESTAMPTZ,
    ADD COLUMN deletion_last_error TEXT;

ALTER TABLE recordings
    ADD CONSTRAINT recordings_deletion_status_check
        CHECK (deletion_status IN (
            'ACTIVE', 'TRASHED', 'PURGE_REQUESTED',
            'PURGING', 'PURGE_FAILED', 'DELETED'
        ));

CREATE UNIQUE INDEX uk_recordings_deletion_id
    ON recordings(deletion_id)
    WHERE deletion_id IS NOT NULL;

CREATE INDEX idx_recordings_trash_expiry
    ON recordings(deletion_status, purge_after);

CREATE INDEX idx_recordings_purge_retry
    ON recordings(deletion_status, deletion_next_retry_at, updated_at);
