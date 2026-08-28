package com.manhduc205.meetingplatform.models.dtos.response;

import com.manhduc205.meetingplatform.enums.InvitationStatus;
import com.manhduc205.meetingplatform.enums.MeetingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CalendarMeetingResponse {
    private String id;
    private String meetingCode;
    private String title;
    private String description;
    private String hostId;
    private String hostName;
    private String hostAvatarUrl;
    private Instant plannedStartTime;
    private Instant plannedEndTime;
    private MeetingStatus status;
    private Boolean isHost;
    private String role;
    private InvitationStatus invitationStatus;
    private boolean canStart;
    private boolean canJoin;
}
