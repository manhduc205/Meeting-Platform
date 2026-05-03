package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.JoinMeetingRequest;
import com.manhduc205.meetingplatform.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.JoinMeetingResponse;
import com.manhduc205.meetingplatform.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.services.MeetingService;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingParticipantService participantService;

    @PostMapping("/create")
    public ResponseEntity<MeetingResponse> createMeeting(@RequestBody MeetingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingService.createMeeting(request));
    }

    @PutMapping("/{meetingCode}/end")
    public ResponseEntity<MeetingResponse> endMeeting(@PathVariable String meetingCode) {
        return ResponseEntity.ok(meetingService.endMeeting(meetingCode));
    }

    @PostMapping("/{meetingCode}/join")
    public ResponseEntity<JoinMeetingResponse> joinMeeting(
            @PathVariable String meetingCode,
            @RequestBody JoinMeetingRequest request) {
        return ResponseEntity.ok(participantService.joinMeeting(meetingCode, request.getMeetingPassword()));
    }
}