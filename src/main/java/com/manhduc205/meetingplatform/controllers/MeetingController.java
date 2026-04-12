package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.JoinMeetingRequest;
import com.manhduc205.meetingplatform.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.ActiveParticipantsResponse;
import com.manhduc205.meetingplatform.dtos.response.JoinMeetingResponse;
import com.manhduc205.meetingplatform.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.dtos.response.ParticipantDto;
import com.manhduc205.meetingplatform.services.MeetingService;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {
    private final MeetingService meetingService;
    private final MeetingParticipantService participantService;

    @PostMapping("/create")
    public ResponseEntity<MeetingResponse> createMeeting(@RequestBody MeetingCreateRequest request) {
        MeetingResponse response = meetingService.createMeeting(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{meetingCode}/end")
    public ResponseEntity<MeetingResponse> endMeeting(@PathVariable String meetingCode) {
        MeetingResponse response = meetingService.endMeeting(meetingCode);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/{meetingCode}/participants")
    public ResponseEntity<List<ParticipantDto>> getAllParticipants(
            @PathVariable("meetingCode") String meetingCode) {
        List<ParticipantDto> participants = participantService.getAllParticipants(meetingCode);
        return ResponseEntity.ok(participants);
    }
    /**
     * Lấy danh sách người đang họp cho phòng chờ
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
            @RequestBody JoinMeetingRequest request) {
        log.info("Controller: User joining meeting [{}]", meetingCode);
        String meetingPassword = request.getMeetingPassword();
        JoinMeetingResponse response = participantService.joinMeeting(
                meetingCode,
                meetingPassword);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
