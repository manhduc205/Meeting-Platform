package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.services.HostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingCode}/host")
public class HostController {

    private final HostService hostService;

    /**
     LOCK_MEETING, WAITING_ROOM, DISABLE_SCREEN_SHARE
     */
    @PutMapping("/settings")
    public ResponseEntity<Void> updateSettings(
            @PathVariable String meetingCode,
            @RequestParam String type,
            @RequestParam boolean enabled,
            @AuthenticationPrincipal Jwt jwt) {

        hostService.updateSecuritySetting(meetingCode, jwt.getSubject(), type, enabled);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/command")
    public ResponseEntity<Void> executeCommand(
            @PathVariable String meetingCode,
            @RequestParam String command,
            @RequestParam(required = false) String targetId,
            @AuthenticationPrincipal Jwt jwt) {

        // Phân luồng lệnh gọi thẳng vào Service
        if ("MUTE_ALL".equals(command)) {
            hostService.muteAll(meetingCode, jwt.getSubject());
        } else if ("KICK_PARTICIPANT".equals(command)) {
            hostService.kickUser(meetingCode, jwt.getSubject(), targetId);
        } else {
            throw new IllegalArgumentException("Lệnh không được hỗ trợ: " + command);
        }

        return ResponseEntity.ok().build();
    }
}