package com.manhduc205.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscriptCompletedMessage(
        String messageId,
        String eventType,
        String jobId,
        Long recordingId,
        String language,
        Integer version,
        String rawTranscriptObjectKey,
        String captionObjectKey,
        String summaryObjectKey,
        String summary,
        List<KeyMoment> keyMoments,
        Integer segmentCount,
        String model,
        Instant completedAt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeyMoment(Long startMs, Long endMs, String topic) {
    }
}
