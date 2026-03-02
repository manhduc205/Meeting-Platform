package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.MeetingCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.services.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meetings")
public class MeetingController {
    private final MeetingService meetingService;
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
}
