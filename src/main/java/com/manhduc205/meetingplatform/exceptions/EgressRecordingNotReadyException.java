package com.manhduc205.meetingplatform.exceptions;

/**
 * Short race between LiveKit creating an Egress and the backend committing
 * its Recording row. HTTP 503 asks LiveKit to retry after the row is visible.
 */
public class EgressRecordingNotReadyException extends RuntimeException {

    public EgressRecordingNotReadyException(String egressId) {
        super("Recording chưa sẵn sàng cho Egress " + egressId);
    }
}
