package com.manhduc205.meetingplatform.models.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class MeetingResponse {
    private String id;
    private String meetingCode;
    private String meetingPassword;
    private String title;
    private String description;
    private String hostId;
    private String status;
    private Instant startTime;
    private Instant endTime;
    private boolean isWaitingRoomEnabled;
    private Instant createdAt;
    private String googleEventId;
}