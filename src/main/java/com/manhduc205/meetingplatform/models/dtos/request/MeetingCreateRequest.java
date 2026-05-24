package com.manhduc205.meetingplatform.models.dtos.request;

import lombok.Data;
import java.time.Instant;

@Data
public class MeetingCreateRequest {
    private String title;
    private String description;
    private String meetingPassword;
    private Boolean isWaitingRoomEnabled;
    private Instant startTime;
    private Instant endTime;
}