package com.manhduc205.AI_application.service;

import com.manhduc205.AI_application.dto.request.YoutubeSummaryRequest;
import com.manhduc205.AI_application.dto.response.YoutubeSummaryResponse;
import com.manhduc205.AI_application.dto.response.YoutubeTranscriptPageResponse;

public interface YoutubeSummaryService {
    YoutubeSummaryResponse create(YoutubeSummaryRequest request);

    YoutubeSummaryResponse get(String summaryId);

    YoutubeTranscriptPageResponse getTranscript(String summaryId, String cursor, int limit);

    void markPublished(String summaryId);

    void markInfrastructureFailed(String summaryId, String error);
}
