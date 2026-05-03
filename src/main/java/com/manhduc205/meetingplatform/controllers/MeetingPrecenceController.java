package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.SignalingMessage;
import com.manhduc205.meetingplatform.enums.*;
import com.manhduc205.meetingplatform.services.HeartbeatService;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MeetingPrecenceController {
    private static final String MEETING_CODE_ATTR = "meetingCode";
    private static final String USER_ID_ATTR = "userId";

    private final SimpMessagingTemplate messagingTemplate;
    private final MeetingPresenceService presenceService;
    private final HeartbeatService heartbeatService;

    @MessageMapping("/meeting.signal")
    public void processSignaling(@Payload SignalingMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String roomTopic = "/topic/meeting." + message.getMeetingCode();
        message.setTimestamp(LocalDateTime.now());

        switch (message.getCategory()) {
            case PRESENCE -> {
                Optional.ofNullable(headerAccessor)
                        .map(SimpMessageHeaderAccessor::getSessionAttributes)
                        .ifPresent(attrs -> {
                            attrs.put(MEETING_CODE_ATTR, message.getMeetingCode());
                            attrs.put(USER_ID_ATTR, message.getSenderId());
                        });

                List<SignalingMessage> responses = presenceService.handlePresenceUpdate(message);
                responses.forEach(res -> messagingTemplate.convertAndSend(roomTopic, res));
            }

            case SIGNALING -> messagingTemplate.convertAndSendToUser(
                    message.getTargetId(), "/queue/signaling", message);

            case ACTION -> presenceService.handleActionMessage(message);
        }
    }

    /**
     * ✅ ENTERPRISE: Heartbeat PING handler
     * Client gửi PING message mỗi 30s để báo hiệu user vẫn online
     * Server trả PONG để confirm + update heartbeat TTL
     *
     * Message format từ client:
     * {
     *   "type": "PING",
     *   "meetingCode": "ABC-DEF-GHI",
     *   "senderId": "user123",
     *   "timestamp": "2026-03-21T10:30:45"
     * }
     */
    @MessageMapping("/meeting.heartbeat")
    public void handleHeartbeat(@Payload Map<String, Object> payload) {
        try {
            String meetingCode = (String) payload.get("meetingCode");
            String userId = (String) payload.get("senderId");
            String type = (String) payload.get("type");

            log.debug("💓 Received heartbeat message from user [{}] in room [{}], type: {}", userId, meetingCode, type);

            if ("PING".equals(type)) {
                // Update heartbeat key in Redis (set TTL = 65s)
                heartbeatService.updateHeartbeat(meetingCode, userId);

                // Send PONG response back to client
                Map<String, Object> pongResponse = new HashMap<>();
                pongResponse.put("type", "PONG");
                pongResponse.put("meetingCode", meetingCode);
                pongResponse.put("timestamp", System.currentTimeMillis());
                pongResponse.put("message", "Server received your heartbeat");

                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/heartbeat",
                        pongResponse
                );
                log.debug("✅ Sent PONG response to user [{}]", userId);
            }
        } catch (Exception e) {
            log.error("❌ Error handling heartbeat message", e);
        }
    }
}