ALTER TABLE recordings
    ADD COLUMN storage_prefix VARCHAR(255);

-- A previous entity version mapped visibility to RecordingStatus, so Hibernate
-- may already have created this constraint with STARTING/RECORDING values.
ALTER TABLE recordings
    DROP CONSTRAINT IF EXISTS recordings_visibility_check;

UPDATE recordings
SET visibility = 'MEETING_MEMBERS'
WHERE visibility IS NULL;

ALTER TABLE recordings
    ALTER COLUMN visibility SET DEFAULT 'MEETING_MEMBERS',
    ALTER COLUMN visibility SET NOT NULL;

ALTER TABLE recordings
    ADD CONSTRAINT recordings_visibility_check
        CHECK (visibility IN ('PRIVATE', 'MEETING_MEMBERS', 'LINK_ONLY', 'SELECTED_USERS'));

CREATE INDEX idx_recordings_meeting_created_at ON recordings(meeting_code, created_at DESC);
