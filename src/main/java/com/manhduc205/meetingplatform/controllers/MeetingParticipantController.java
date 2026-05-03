package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.response.ActiveParticipantsResponse;
import com.manhduc205.meetingplatform.dtos.response.ParticipantDto;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingCode}/participants")
public class MeetingParticipantController {

    private final MeetingParticipantService participantService;

    @GetMapping
    public ResponseEntity<List<ParticipantDto>> getAllParticipants(@PathVariable String meetingCode) {
        return ResponseEntity.ok(participantService.getAllParticipants(meetingCode));
    }

    @GetMapping("/active")
    public ResponseEntity<ActiveParticipantsResponse> getActiveParticipants(@PathVariable String meetingCode) {
        return ResponseEntity.ok(participantService.getActiveParticipants(meetingCode));
    }

    // Cái em cần nhất cho danh sách bên tay phải
    @GetMapping("/sidebar")
    public ResponseEntity<List<ParticipantDto>> getSidebarParticipants(@PathVariable String meetingCode) {
        return ResponseEntity.ok(participantService.getSidebarParticipants(meetingCode));
    }
}