package com.manhduc205.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscriptFailedMessage(
        String messageId,
        String eventType,
        String jobId,
        Long recordingId,
        Integer version,
        String errorCode,
        String errorMessage,
        Instant failedAt
) {
}
