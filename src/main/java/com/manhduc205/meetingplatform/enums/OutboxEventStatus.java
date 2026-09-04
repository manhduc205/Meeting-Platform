package com.manhduc205.meetingplatform.enums;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    RETRY,
    COMPLETED,
    FAILED
}
