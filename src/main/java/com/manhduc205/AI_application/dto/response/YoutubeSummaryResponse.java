package com.manhduc205.AI_application.dto.response;

import java.time.Instant;
import java.util.List;

public record YoutubeSummaryResponse(
        String id,
        String videoId,
        String sourceUrl,
        String status,
        String targetLanguage,
        String sourceLanguage,
        String summary,
        List<KeyMoment> keyMoments,
        long transcriptSegmentCount,
        String error,
        Instant requestedAt,
        Instant completedAt
) {
    public record KeyMoment(Long startMs, Long endMs, String topic) {
    }
}
