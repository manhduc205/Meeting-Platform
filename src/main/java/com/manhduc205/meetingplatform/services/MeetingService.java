package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.models.dtos.response.MeetingResponse;
import java.util.List;

public interface MeetingService {
    MeetingResponse createMeeting(MeetingCreateRequest request);
    MeetingResponse endMeeting(String meetingCode);
    List<MeetingResponse> getMyMeetings();
    MeetingResponse updateMeeting(String meetingCode, MeetingCreateRequest request);
    void cancelMeeting(String meetingCode);
}