package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.dtos.response.ActiveParticipantsResponse;
import com.manhduc205.meetingplatform.dtos.response.JoinMeetingResponse;
import com.manhduc205.meetingplatform.dtos.response.ParticipantDto;
import com.manhduc205.meetingplatform.dtos.response.RaisedHandResponse;

import java.util.List;

/**
 * Service quản lý participant và logic join phòng
 */
public interface MeetingParticipantService {

    ActiveParticipantsResponse getActiveParticipants(String meetingCode);
    List<ParticipantDto> getAllParticipants(String meetingCode);
    RaisedHandResponse getRaisedHands(String meetingCode);

    JoinMeetingResponse joinMeeting(String meetingCode, String meetingPassword);

    void toggleRaiseHand(String meetingCode, boolean isRaising);
}
