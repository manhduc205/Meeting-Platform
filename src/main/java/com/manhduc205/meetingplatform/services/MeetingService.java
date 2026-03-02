package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.MeetingResponse;
import org.springframework.stereotype.Service;


public interface MeetingService {
    MeetingResponse createMeeting(MeetingCreateRequest request, String hostId);
    MeetingResponse endMeeting(String meetingId, String hostId);
}
