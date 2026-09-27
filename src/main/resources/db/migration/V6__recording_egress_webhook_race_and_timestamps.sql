-- Recording timestamps used to be timezone-less values written by a container
-- configured for Vietnam. Convert those existing values to real instants so
-- JSON includes an offset and browser clients cannot reinterpret them as UTC.
ALTER TABLE recordings
    ALTER COLUMN created_at TYPE TIMESTAMPTZ
        USING created_at AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ
        USING updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh';

-- LiveKit can POST its first Egress status before startRoomCompositeEgress has
-- returned to the application. Persist unknown events and replay them once the
-- recording row has been created instead of dropping the state transition.
CREATE TABLE pending_egress_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    egress_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pending_egress_webhook_events_egress_received
    ON pending_egress_webhook_events(egress_id, received_at, id);
