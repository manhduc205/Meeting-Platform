package com.manhduc205.meetingplatform.models.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class InvitationCreateRequest {
    @NotEmpty(message = "Cần có ít nhất một email khách mời.")
    private List<@Email(message = "Email khách mời không hợp lệ.") String> inviteeEmails;
}
