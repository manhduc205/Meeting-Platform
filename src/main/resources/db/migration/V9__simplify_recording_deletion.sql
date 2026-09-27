UPDATE recordings
SET purge_after = CASE
    WHEN deletion_status = 'ACTIVE' THEN NULL
    WHEN deletion_status = 'TRASHED' THEN COALESCE(purge_after, CURRENT_TIMESTAMP + INTERVAL '3 days')
    ELSE CURRENT_TIMESTAMP
END;

UPDATE recording_ai_jobs
SET status = 'FAILED',
    last_error = COALESCE(last_error, 'Recording deletion cancelled this job'),
    completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP)
WHERE status = 'CANCELLED';

UPDATE outbox_events
SET status = 'FAILED',
    last_error = COALESCE(last_error, 'Recording deletion cancelled this event'),
    completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP)
WHERE status = 'CANCELLED';

DROP INDEX IF EXISTS uk_recordings_deletion_id;
DROP INDEX IF EXISTS idx_recordings_trash_expiry;
DROP INDEX IF EXISTS idx_recordings_purge_retry;

ALTER TABLE recordings
    DROP CONSTRAINT IF EXISTS recordings_deletion_status_check,
    DROP COLUMN deletion_status,
    DROP COLUMN trashed_at,
    DROP COLUMN deletion_id,
    DROP COLUMN deletion_requested_at,
    DROP COLUMN deleted_at,
    DROP COLUMN deletion_attempt_count,
    DROP COLUMN deletion_next_retry_at,
    DROP COLUMN deletion_last_error;

CREATE INDEX idx_recordings_purge_after
    ON recordings(purge_after)
    WHERE purge_after IS NOT NULL;
