package com.manhduc205.AI_application.dto.response;

import java.time.Instant;

public record RecordingSummaryResponse(
        Long recordingId,
        String language,
        Integer version,
        String contentMarkdown,
        String model,
        Instant generatedAt
) {
}
