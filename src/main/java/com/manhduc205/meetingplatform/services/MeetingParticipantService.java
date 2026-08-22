package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.enums.ParticipantRole;
import com.manhduc205.meetingplatform.models.dtos.response.*;
import com.manhduc205.meetingplatform.enums.WaitingRoomAction;

import java.util.List;

public interface MeetingParticipantService {
    ActiveParticipantsResponse getActiveParticipants(String meetingCode); // Cho màn hình Lobby
    List<ParticipantDto> getAllParticipants(String meetingCode); // Cơ bản
    List<ParticipantDto> getSidebarParticipants(String meetingCode); // Cho Sidebar (Có Host, Hand)
    List<ParticipantAttendanceResponse> getMeetingAttendanceHistory(String meetingCode);
    JoinMeetingResponse joinMeeting(String meetingCode, String meetingPassword);
    void leaveMeeting(String meetingCode);
    void removeParticipantByHost(String meetingCode, String userId);
    List<ParticipantDto> getWaitingParticipants(String meetingCode);
    void processWaitingParticipants(String meetingCode, List<String> userIds, WaitingRoomAction action);

    void toggleRaiseHand(String meetingCode, boolean isRaising);
    RaisedHandResponse getRaisedHands(String meetingCode);
}
