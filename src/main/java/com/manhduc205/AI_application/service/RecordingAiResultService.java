package com.manhduc205.AI_application.service;

import com.manhduc205.routing.dto.TranscriptCompletedMessage;
import com.manhduc205.routing.dto.TranscriptFailedMessage;

public interface RecordingAiResultService {
    void handleCompleted(TranscriptCompletedMessage message) throws Exception;

    void handleFailed(TranscriptFailedMessage message);
}
