package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.models.dtos.response.MeetingResponse;

public interface MeetingService {
    MeetingResponse createMeeting(MeetingCreateRequest request);

    MeetingResponse endMeeting(String meetingId);
}
