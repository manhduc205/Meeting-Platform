package com.manhduc205.AI_application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TranscriptRequestResponse {
    private String jobId;
    private Long recordingId;
    private String status;
    private String language;
    private Integer version;
    private Instant requestedAt;
}
