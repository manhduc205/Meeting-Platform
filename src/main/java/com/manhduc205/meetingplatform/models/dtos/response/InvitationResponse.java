package com.manhduc205.meetingplatform.models.dtos.response;

import com.manhduc205.meetingplatform.enums.InvitationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class InvitationResponse {
    private String id;
    private String inviteeEmail;
    private InvitationStatus status;
    private Instant respondedAt;
    private Instant createdAt;
}
