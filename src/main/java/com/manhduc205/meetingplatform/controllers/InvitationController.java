package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.request.InvitationResponseRequest;
import com.manhduc205.meetingplatform.models.dtos.response.InvitationResponse;
import com.manhduc205.meetingplatform.services.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {
    private final MeetingService meetingService;

    @PatchMapping("/{invitationId}")
    public ResponseEntity<InvitationResponse> respondToInvitation(
            @PathVariable String invitationId, @Valid @RequestBody InvitationResponseRequest request) {
        return ResponseEntity.ok(meetingService.respondToInvitation(invitationId, request));
    }
}
