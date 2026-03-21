package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.dtos.response.ActiveParticipantsResponse;
import com.manhduc205.meetingplatform.dtos.response.JoinMeetingResponse;

/**
 * Service quản lý participant và logic join phòng
 */
public interface MeetingParticipantService {

    ActiveParticipantsResponse getActiveParticipants(String meetingCode);

    JoinMeetingResponse joinMeeting(String meetingCode, String userId, String meetingPassword);
}

