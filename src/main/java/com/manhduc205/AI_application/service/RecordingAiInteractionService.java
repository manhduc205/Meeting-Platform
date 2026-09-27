package com.manhduc205.AI_application.service;

import com.manhduc205.AI_application.dto.request.RecordingChatRequest;
import com.manhduc205.AI_application.dto.response.ArtifactUrlResponse;
import com.manhduc205.AI_application.dto.response.RecordingSummaryResponse;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface RecordingAiInteractionService {
    RecordingSummaryResponse getSummary(Long recordingId);

    void deleteSummary(Long recordingId);

    StreamingResponseBody streamSummary(Long recordingId);

    StreamingResponseBody streamChat(Long recordingId, RecordingChatRequest request);

    ArtifactUrlResponse createArtifactUrl(Long recordingId, String type);
}
