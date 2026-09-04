package com.manhduc205.routing.dto;

import java.time.Instant;

public record TranscriptRequestedMessage(
        String messageId,
        String eventType,
        String jobId,
        Long recordingId,
        String storagePrefix,
        String videoObjectKey,
        String rawTranscriptObjectKey,
        String captionObjectKey,
        String summaryObjectKey,
        String sourceLanguage,
        String targetLanguage,
        String requestedBy,
        Integer version,
        Instant requestedAt
) {
}
