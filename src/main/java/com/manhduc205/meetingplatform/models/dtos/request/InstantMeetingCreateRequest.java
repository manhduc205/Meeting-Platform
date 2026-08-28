package com.manhduc205.meetingplatform.models.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class InstantMeetingCreateRequest {
    @NotBlank(message = "Tên cuộc họp là bắt buộc.")
    private String title;
    private String description;
    private String meetingPassword;
    private Boolean isWaitingRoomEnabled;
    private List<@Email(message = "Email khách mời không hợp lệ.") String> inviteeEmails = List.of();
}
