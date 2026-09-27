UPDATE recording_ai_jobs
SET status = 'PUBLISHED'
WHERE status = 'PROCESSING';

DROP INDEX IF EXISTS uk_recording_ai_active_job;

CREATE UNIQUE INDEX uk_recording_ai_active_job
    ON recording_ai_jobs(recording_id, operation)
    WHERE status IN ('REQUESTED', 'PUBLISHED');
