package com.manhduc205.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscriptCompletedMessage(
        Integer schemaVersion,
        String messageId,
        String eventType,
        String jobId,
        Long recordingId,
        String language,
        Integer version,
        String rawTranscriptObjectKey,
        String captionObjectKey,
        String summaryObjectKey,
        String transcriptSha256,
        Integer segmentCount,
        String model,
        Instant completedAt
) {
}
