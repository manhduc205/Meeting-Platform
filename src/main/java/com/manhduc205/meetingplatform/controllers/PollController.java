package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.PollCreateRequest;
import com.manhduc205.meetingplatform.dtos.response.PollResponse;
import com.manhduc205.meetingplatform.services.Impl.PollServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingCode}/polls")
public class PollController {

    private final PollServiceImpl pollService;

    @PostMapping
    public ResponseEntity<PollResponse> createPoll(
            @PathVariable String meetingCode,
            @RequestBody PollCreateRequest request) {

        PollResponse response = pollService.createPoll(meetingCode , request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{pollId}/vote")
    public ResponseEntity<Void> submitVote(
            @PathVariable String meetingCode,
            @PathVariable String pollId,
            @RequestParam String optionId) {

        pollService.submitVote(meetingCode, pollId, optionId );
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{pollId}/close")
    public ResponseEntity<Void> closePoll(
            @PathVariable String meetingCode,
            @PathVariable String pollId) {

        pollService.closePoll(meetingCode, pollId);
        return ResponseEntity.ok().build();
    }
}