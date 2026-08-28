CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    keycloak_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255),
    avatar_url VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE meetings (
    id VARCHAR(255) PRIMARY KEY,
    meeting_code VARCHAR(255) NOT NULL UNIQUE,
    meeting_password VARCHAR(255),
    host_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    google_event_id VARCHAR(255),
    is_locked BOOLEAN,
    is_screen_share_disabled BOOLEAN,
    status VARCHAR(20) NOT NULL,
    is_waiting_room_enabled BOOLEAN,
    created_at TIMESTAMPTZ
);
CREATE INDEX idx_meeting_code ON meetings(meeting_code);
CREATE INDEX idx_host_id ON meetings(host_id);

CREATE TABLE meeting_participants (
    id BIGSERIAL PRIMARY KEY,
    meeting_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    joined_once_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_meeting_user UNIQUE(meeting_id, user_id)
);
CREATE INDEX idx_meeting_joined_time ON meeting_participants(meeting_id, joined_once_at);

CREATE TABLE recordings (
    id BIGSERIAL PRIMARY KEY,
    meeting_code VARCHAR(255) NOT NULL,
    egress_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(255) NOT NULL,
    thumbnail_url TEXT,
    file_url TEXT,
    duration BIGINT,
    visibility VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE recording_shares (
    id BIGSERIAL PRIMARY KEY,
    recording_egress_id VARCHAR(255) NOT NULL,
    shared_with_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP
);
CREATE INDEX idx_share_recording_user ON recording_shares(recording_egress_id, shared_with_user_id);
