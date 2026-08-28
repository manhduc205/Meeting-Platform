package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.request.*;
import com.manhduc205.meetingplatform.models.dtos.response.InvitationResponse;
import com.manhduc205.meetingplatform.models.dtos.response.JoinMeetingResponse;
import com.manhduc205.meetingplatform.models.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.models.dtos.response.ParticipantAttendanceResponse;
import com.manhduc205.meetingplatform.services.MeetingService;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingParticipantService participantService;

    @PostMapping({"", "/scheduled", "/create"})
    public ResponseEntity<MeetingResponse> createMeeting(@Valid @RequestBody MeetingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.createMeeting(request));
    }

    @PostMapping("/instant")
    public ResponseEntity<MeetingResponse> createInstantMeeting(@Valid @RequestBody InstantMeetingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.createInstantMeeting(request));
    }

    @GetMapping("/my-meetings")
    public ResponseEntity<List<MeetingResponse>> getMyScheduledMeetings() {
        return ResponseEntity.ok(meetingService.getMyMeetings());
    }

    @PutMapping("/{meetingCode}")
    public ResponseEntity<MeetingResponse> updateMeeting(
            @PathVariable String meetingCode,
            @Valid @RequestBody MeetingUpdateRequest request) {
        return ResponseEntity.ok(meetingService.updateMeeting(meetingCode, request));
    }

    @PostMapping("/{meetingCode}/start")
    public ResponseEntity<MeetingResponse> startMeeting(@PathVariable String meetingCode) {
        return ResponseEntity.ok(meetingService.startMeeting(meetingCode));
    }

    @DeleteMapping("/{meetingCode}")
    public ResponseEntity<Void> cancelMeeting(@PathVariable String meetingCode) {
        meetingService.cancelMeeting(meetingCode);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{meetingCode}/end")
    public ResponseEntity<MeetingResponse> endMeeting(@PathVariable String meetingCode) {
        return ResponseEntity.ok(meetingService.endMeeting(meetingCode));
    }

    @PostMapping("/{meetingCode}/invitations")
    public ResponseEntity<List<InvitationResponse>> addInvitations(
            @PathVariable String meetingCode, @Valid @RequestBody InvitationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.addInvitations(meetingCode, request));
    }

    @GetMapping("/{meetingCode}/invitations")
    public ResponseEntity<List<InvitationResponse>> getInvitations(@PathVariable String meetingCode) {
        return ResponseEntity.ok(meetingService.getInvitations(meetingCode));
    }

    @PostMapping("/{meetingCode}/join")
    public ResponseEntity<JoinMeetingResponse> joinMeeting(
            @PathVariable String meetingCode,
            @RequestBody JoinMeetingRequest request) {
        return ResponseEntity.ok(participantService.joinMeeting(meetingCode, request.getMeetingPassword()));
    }

    @PostMapping("/{meetingCode}/leave")
    public ResponseEntity<Void> leaveMeeting(@PathVariable String meetingCode) {
        participantService.leaveMeeting(meetingCode);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{meetingCode}/attendance")
    public ResponseEntity<List<ParticipantAttendanceResponse>> getMeetingAttendance(@PathVariable String meetingCode) {
        return ResponseEntity.ok(participantService.getMeetingAttendanceHistory(meetingCode));
    }
}
