package com.manhduc205.AI_application.service;

import com.manhduc205.routing.dto.YoutubeSummaryCompletedMessage;
import com.manhduc205.routing.dto.YoutubeSummaryFailedMessage;

public interface YoutubeSummaryResultService {
    void handleCompleted(YoutubeSummaryCompletedMessage message) throws Exception;

    void handleFailed(YoutubeSummaryFailedMessage message);
}
