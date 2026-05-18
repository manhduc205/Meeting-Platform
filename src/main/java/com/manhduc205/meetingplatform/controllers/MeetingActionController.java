package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.response.RaisedHandResponse;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingCode}/actions")
public class MeetingActionController {

    private final MeetingParticipantService participantService;

    // Giơ / Hạ tay (Kích hoạt Delta Broadcast)
    @PostMapping("/raise-hand")
    public ResponseEntity<Void> toggleRaiseHand(
            @PathVariable String meetingCode,
            @RequestParam boolean isRaising) {

        participantService.toggleRaiseHand(meetingCode, isRaising);
        return ResponseEntity.ok().build();
    }

    // Đồng bộ ban đầu khi mới vào phòng
    @GetMapping("/raised-hands")
    public ResponseEntity<RaisedHandResponse> getInitialRaisedHands(@PathVariable String meetingCode) {
        return ResponseEntity.ok(participantService.getRaisedHands(meetingCode));
    }
}