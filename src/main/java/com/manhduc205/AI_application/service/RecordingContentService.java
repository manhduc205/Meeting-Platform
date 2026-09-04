package com.manhduc205.AI_application.service;

import com.manhduc205.AI_application.dto.response.RecordingDetailResponse;
import com.manhduc205.AI_application.dto.response.TranscriptSegmentPageResponse;

public interface RecordingContentService {
    RecordingDetailResponse getRecordingDetail(Long recordingId);

    TranscriptSegmentPageResponse getTranscriptSegments(Long recordingId, String language, String cursor, int limit);
}
