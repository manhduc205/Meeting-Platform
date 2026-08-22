package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.request.WaitingRoomActionRequest;
import com.manhduc205.meetingplatform.models.dtos.response.ParticipantDto;
import com.manhduc205.meetingplatform.services.HostService;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingCode}/host")
public class HostController {

    private final HostService hostService;
    private final MeetingParticipantService participantService;

    @PutMapping("/settings")
    public ResponseEntity<Void> updateSettings(
            @PathVariable String meetingCode,
            @RequestParam String type,
            @RequestParam boolean enabled) {
        hostService.updateSecuritySetting(meetingCode, type, enabled);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/command")
    public ResponseEntity<Void> executeCommand(
            @PathVariable String meetingCode,
            @RequestParam String command,
            @RequestParam(required = false) String targetId) {
        if ("MUTE_ALL".equals(command)) hostService.muteAll(meetingCode);
        else if ("MUTE_PARTICIPANT".equals(command)) hostService.muteParticipant(meetingCode, targetId);
        else if ("KICK_PARTICIPANT".equals(command)) hostService.kickUser(meetingCode, targetId);
        else throw new IllegalArgumentException("Lệnh không được hỗ trợ: " + command);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/waiting-room")
    public ResponseEntity<List<ParticipantDto>> getWaitingParticipants(
            @PathVariable String meetingCode) {
        return ResponseEntity.ok(participantService.getWaitingParticipants(meetingCode));
    }

    @PostMapping("/waiting-room/action")
    public ResponseEntity<Void> processWaitingRoom(
            @PathVariable String meetingCode,
            @RequestBody WaitingRoomActionRequest request) {
        participantService.processWaitingParticipants(
                meetingCode, request.getUserIds(), request.getAction()
        );
        return ResponseEntity.ok().build();
    }
}
