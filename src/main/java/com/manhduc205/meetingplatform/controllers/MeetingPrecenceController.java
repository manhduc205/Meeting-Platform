package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.request.SignalingMessage;
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
import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MeetingPrecenceController {
    private static final String MEETING_CODE_ATTR = "meetingCode";

    private final SimpMessagingTemplate messagingTemplate;
    private final MeetingPresenceService presenceService;
    private final HeartbeatService heartbeatService;

    @MessageMapping("/meeting.signal")
    public void processSignaling(@Payload SignalingMessage message, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        if (principal == null) {
            throw new SecurityException("WebSocket chưa được xác thực");
        }
        // Never trust a client-supplied senderId. The authenticated STOMP principal is
        // the internal user ID used by REST responses and LiveKit identities.
        message.setSenderId(principal.getName());
        String roomTopic = "/topic/meeting." + message.getMeetingCode();
        message.setTimestamp(LocalDateTime.now());

        switch (message.getCategory()) {
            case PRESENCE -> {
                Optional.ofNullable(headerAccessor)
                        .map(SimpMessageHeaderAccessor::getSessionAttributes)
                        .ifPresent(attrs -> {
                            Object boundMeetingCode = attrs.get(MEETING_CODE_ATTR);
                            if (boundMeetingCode != null && !boundMeetingCode.equals(message.getMeetingCode())) {
                                throw new SecurityException("Một WebSocket session chỉ được tham gia một cuộc họp");
                            }
                            attrs.put(MEETING_CODE_ATTR, message.getMeetingCode());
                        });

                String sessionId = headerAccessor == null ? null : headerAccessor.getSessionId();
                List<SignalingMessage> responses = presenceService.handlePresenceUpdate(message, sessionId);
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
     * Message format from client. Identity, session, and meeting are derived
     * from the authenticated WebSocket session, never from this payload:
     * {
     *   "type": "PING",
     *   "timestamp": "2026-03-21T10:30:45"
     * }
     */
    @MessageMapping("/meeting.heartbeat")
    public void handleHeartbeat(@Payload Map<String, Object> payload,
                                SimpMessageHeaderAccessor headerAccessor,
                                Principal principal) {
        if (principal == null || headerAccessor == null || headerAccessor.getSessionId() == null) {
            throw new SecurityException("WebSocket chưa được xác thực");
        }
        if (!"PING".equals(payload.get("type"))) {
            throw new IllegalArgumentException("Heartbeat phải có type PING");
        }

        String meetingCode = Optional.ofNullable(headerAccessor.getSessionAttributes())
                .map(attrs -> (String) attrs.get(MEETING_CODE_ATTR))
                .orElseThrow(() -> new SecurityException("Session chưa tham gia cuộc họp"));
        String userId = principal.getName();
        boolean accepted = heartbeatService.updateHeartbeat(meetingCode, userId, headerAccessor.getSessionId());
        if (!accepted) {
            throw new SecurityException("Heartbeat không khớp với session hiện tại");
        }

        Map<String, Object> pongResponse = new HashMap<>();
        pongResponse.put("type", "PONG");
        pongResponse.put("meetingCode", meetingCode);
        pongResponse.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSendToUser(userId, "/queue/heartbeat", pongResponse);
    }
}
