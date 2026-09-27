CREATE TABLE recording_ai_jobs (
    id VARCHAR(36) PRIMARY KEY,
    recording_id BIGINT NOT NULL REFERENCES recordings(id) ON DELETE CASCADE,
    requested_by VARCHAR(255) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    language VARCHAR(20) NOT NULL,
    version INTEGER NOT NULL,
    last_error TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_recording_ai_job_version UNIQUE(recording_id, operation, version)
);

CREATE INDEX idx_recording_ai_jobs_recording_version
    ON recording_ai_jobs(recording_id, operation, version DESC);

CREATE UNIQUE INDEX uk_recording_ai_active_job
    ON recording_ai_jobs(recording_id, operation)
    WHERE status IN ('REQUESTED', 'PUBLISHED', 'PROCESSING');
