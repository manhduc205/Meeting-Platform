package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.SignalingMessage;
import com.manhduc205.meetingplatform.enums.*;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

import static com.manhduc205.meetingplatform.enums.MessageCategory.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MeetingPrecenceController {
    private final SimpMessagingTemplate messagingTemplate;
    private final MeetingPresenceService presenceService;

    @MessageMapping("/meeting.signal")
    public void processSignaling(@Payload SignalingMessage message) {
        String meetingCode = message.getMeetingCode();
        String roomTopic = "/topic/meeting." + meetingCode;
        message.setTimestamp(LocalDateTime.now());

        // Sử dụng Enum MessageCategory để switch
        switch (message.getCategory()) {
            case PRESENCE -> handlePresence(message, meetingCode, roomTopic);

            case SIGNALING -> {
                log.debug("P2P Signaling: {} -> {}", message.getSenderId(), message.getTargetId());
                // Gửi đích danh qua queue riêng của User
                messagingTemplate.convertAndSendToUser(
                        message.getTargetId(),
                        "/queue/signaling",
                        message
                );
            }

            case ACTION -> messagingTemplate.convertAndSend(roomTopic, message);

            default -> log.warn("Category không xác định: {}", message.getCategory());
        }
    }

    private void handlePresence(SignalingMessage message, String meetingCode, String roomTopic) {
        PresenceType pType = PresenceType.valueOf(message.getType());

        if (PresenceType.JOIN.equals(pType)) {
            presenceService.addOnlineUser(meetingCode, message.getSenderId());
            messagingTemplate.convertAndSend(roomTopic, message);

            // Kiểm tra ngưỡng 50 người để kích hoạt Hybrid
            if (presenceService.shouldSwitchToSfu(meetingCode)) {
                SignalingMessage switchCmd = SignalingMessage.builder()
                        .category(SIGNALING)
                        .type(SignalingType.SWITCH_TO_SFU.name())
                        .meetingCode(meetingCode)
                        .timestamp(LocalDateTime.now())
                        .build();
                messagingTemplate.convertAndSend(roomTopic, switchCmd);
            }
        }
        else if (PresenceType.LEAVE.equals(pType)) {
            presenceService.removeOnlineUser(meetingCode, message.getSenderId());
            messagingTemplate.convertAndSend(roomTopic, message);
        }
    }
}