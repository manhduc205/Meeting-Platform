package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.JoinMeetingRequest;
import com.manhduc205.meetingplatform.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.ActiveParticipantsResponse;
import com.manhduc205.meetingplatform.dtos.response.JoinMeetingResponse;
import com.manhduc205.meetingplatform.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.services.MeetingService;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {
    private final MeetingService meetingService;
    private final MeetingParticipantService participantService;
    @PostMapping("/create")
    public ResponseEntity<MeetingResponse> createMeeting(@RequestBody MeetingCreateRequest request, @AuthenticationPrincipal Jwt jwt) {
        MeetingResponse response = meetingService.createMeeting(request, jwt.getSubject());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @PutMapping("/{meetingCode}/end")
    public ResponseEntity<MeetingResponse> endMeeting(@PathVariable String meetingCode, @AuthenticationPrincipal Jwt jwt) {
        MeetingResponse response = meetingService.endMeeting(meetingCode, jwt.getSubject());
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách người đang họp
     */
    @GetMapping("/{meetingCode}/participants/active")
    public ResponseEntity<ActiveParticipantsResponse> getActiveParticipants(
            @PathVariable String meetingCode) {
        ActiveParticipantsResponse response = participantService.getActiveParticipants(meetingCode);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{meetingCode}/join")
    public ResponseEntity<JoinMeetingResponse> joinMeeting(
            @PathVariable String meetingCode,
            @RequestBody JoinMeetingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Controller: User [{}] joining meeting [{}]", jwt.getSubject(), meetingCode);
        String meetingPassword = request.getMeetingPassword();
        JoinMeetingResponse response = participantService.joinMeeting(
                meetingCode,
                jwt.getSubject(),
                meetingPassword
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
