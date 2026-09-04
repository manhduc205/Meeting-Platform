package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.dtos.request.*;
import com.manhduc205.meetingplatform.models.dtos.response.CalendarMeetingResponse;
import com.manhduc205.meetingplatform.models.dtos.response.InvitationResponse;
import com.manhduc205.meetingplatform.models.dtos.response.MeetingResponse;
import java.time.Instant;
import java.util.List;

public interface MeetingService {
    MeetingResponse createMeeting(MeetingCreateRequest request);
    MeetingResponse createInstantMeeting(InstantMeetingCreateRequest request);
    MeetingResponse getMeeting(String meetingCode);
    MeetingResponse updateMeeting(String meetingCode, MeetingUpdateRequest request);
    MeetingResponse startMeeting(String meetingCode);
    MeetingResponse endMeeting(String meetingCode);
    List<MeetingResponse> getMyMeetings();
    void cancelMeeting(String meetingCode);
    List<CalendarMeetingResponse> getCalendar(Instant from, Instant to);
    List<CalendarMeetingResponse> getUpcoming(int limit);
    List<InvitationResponse> addInvitations(String meetingCode, InvitationCreateRequest request);
    List<InvitationResponse> getInvitations(String meetingCode);
    InvitationResponse respondToInvitation(String invitationId, InvitationResponseRequest request);
}
