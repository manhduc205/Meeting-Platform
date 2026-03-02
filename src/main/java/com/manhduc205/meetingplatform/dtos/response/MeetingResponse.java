package com.manhduc205.meetingplatform.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MeetingResponse {
    private String id;
    private String meetingCode;
    private String title;
    private String description;
    private String hostId;
    private String status;
    private LocalDateTime startTime;
    private boolean isWaitingRoomEnabled;
    private LocalDateTime createdAt;
}