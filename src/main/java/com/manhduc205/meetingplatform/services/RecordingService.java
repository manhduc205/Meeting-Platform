package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.dtos.response.RecordingResponse;
import java.util.List;

public interface RecordingService {
    RecordingResponse startRecording(String meetingCode);
    void stopRecording(String meetingCode, String egressId);
    void stopActiveRecordings(String meetingCode);
    void handleEgressWebhook(String payload);
    List<RecordingResponse> getMeetingRecordings(String meetingCode);
}