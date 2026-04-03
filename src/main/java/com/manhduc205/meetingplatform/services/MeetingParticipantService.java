package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.dtos.response.ActiveParticipantsResponse;
import com.manhduc205.meetingplatform.dtos.response.JoinMeetingResponse;
import com.manhduc205.meetingplatform.dtos.response.RaisedHandResponse;

/**
 * Service quản lý participant và logic join phòng
 */
public interface MeetingParticipantService {

    ActiveParticipantsResponse getActiveParticipants(String meetingCode);
    RaisedHandResponse getRaisedHands(String meetingCode);
    JoinMeetingResponse joinMeeting(String meetingCode, String userId, String meetingPassword);
    public void toggleRaiseHand(String meetingCode, String internalUserId, boolean isRaising);
}

