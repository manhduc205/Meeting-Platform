CREATE TABLE youtube_summaries (
    id VARCHAR(36) PRIMARY KEY,
    requested_by VARCHAR(255) NOT NULL,
    video_id VARCHAR(11) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    target_language VARCHAR(20) NOT NULL,
    storage_prefix VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    last_error TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_youtube_summaries_owner_requested
    ON youtube_summaries(requested_by, requested_at DESC);

CREATE UNIQUE INDEX uk_youtube_summary_active
    ON youtube_summaries(requested_by, video_id, target_language)
    WHERE status IN ('REQUESTED', 'PUBLISHED');
