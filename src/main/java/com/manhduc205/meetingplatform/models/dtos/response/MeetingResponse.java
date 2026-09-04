package com.manhduc205.meetingplatform.models.dtos.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class MeetingResponse {
    private String id;
    private String meetingCode;
    private String title;
    private String description;
    private String hostId;
    private String status;
    private Instant plannedStartTime;
    private Instant plannedEndTime;
    private Instant startedAt;
    private Instant endedAt;
    private boolean waitingRoomEnabled;
    private boolean hasPassword;
    private Instant createdAt;
    private String googleEventId;

    @JsonGetter("isWaitingRoomEnabled")
    public boolean isWaitingRoomEnabled() {
        return waitingRoomEnabled;
    }

    @JsonSetter("isWaitingRoomEnabled")
    public void setWaitingRoomEnabled(boolean waitingRoomEnabled) {
        this.waitingRoomEnabled = waitingRoomEnabled;
    }
}
