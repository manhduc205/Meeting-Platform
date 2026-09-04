package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.exceptions.EgressRecordingNotReadyException;
import com.manhduc205.meetingplatform.services.RecordingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final RecordingService recordingService;

    @PostMapping("/livekit/egress")
    public ResponseEntity<Void> receiveEgressWebhook(@RequestBody String payload) {
        try {
            recordingService.handleEgressWebhook(payload);
            return ResponseEntity.ok().build();
        } catch (EgressRecordingNotReadyException exception) {
            log.warn("Recording chưa commit; yêu cầu LiveKit retry webhook: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
