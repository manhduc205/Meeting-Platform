package com.manhduc205.meetingplatform.dtos.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MeetingCreateRequest {
    private String title;
    private String description;
    private LocalDateTime startTime;
    private Boolean isWaitingRoomEnabled;
}