package com.manhduc205.AI_application.service;

import com.manhduc205.AI_application.dto.request.TranscriptRequestRequest;
import com.manhduc205.AI_application.dto.response.TranscriptRequestResponse;

public interface RecordingAiJobService {
    TranscriptRequestResponse requestTranscript(Long recordingId, TranscriptRequestRequest request);

    void markPublished(String jobId);

    void markInfrastructureFailed(String jobId, String error);
}
