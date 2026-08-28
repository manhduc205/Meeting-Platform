package com.manhduc205.meetingplatform.models.dtos.request;

import com.manhduc205.meetingplatform.enums.InvitationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvitationResponseRequest {
    @NotNull(message = "Trạng thái phản hồi là bắt buộc.")
    private InvitationStatus status;
}
