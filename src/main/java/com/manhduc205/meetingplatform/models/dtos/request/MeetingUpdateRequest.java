package com.manhduc205.meetingplatform.models.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class MeetingUpdateRequest {
    @NotBlank(message = "Tên cuộc họp là bắt buộc.")
    private String title;
    private String description;
    private String meetingPassword;
    private Boolean isWaitingRoomEnabled;
    @NotNull(message = "Thời gian bắt đầu là bắt buộc.")
    private Instant plannedStartTime;
    @NotNull(message = "Thời gian kết thúc là bắt buộc.")
    private Instant plannedEndTime;
}
