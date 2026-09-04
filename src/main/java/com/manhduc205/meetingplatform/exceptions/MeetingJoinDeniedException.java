package com.manhduc205.meetingplatform.exceptions;

public class MeetingJoinDeniedException extends RuntimeException {
    private final String errorCode;

    public MeetingJoinDeniedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
