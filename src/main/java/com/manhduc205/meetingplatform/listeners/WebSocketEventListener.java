package com.manhduc205.meetingplatform.listeners;

import com.manhduc205.meetingplatform.models.dtos.request.SignalingMessage;
import com.manhduc205.meetingplatform.enums.MessageCategory;
import com.manhduc205.meetingplatform.enums.PresenceType;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final MeetingPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());

        // Lấy thông tin User đã lưu ở Interceptor hoặc lúc gửi tin nhắn JOIN
        String userId = Optional.ofNullable(headers.getUser())
                .map(user -> user.getName())
                .orElse(null);

        String meetingCode = Optional.ofNullable(headers.getSessionAttributes())
                .map(attributes -> (String) attributes.get("meetingCode"))
                .orElse(null);
        String sessionId = headers.getSessionId();

        if (userId != null && meetingCode != null && sessionId != null) {
            log.warn("Phát hiện User [{}] rớt mạng khỏi phòng [{}]", userId, meetingCode);

            var transition = presenceService.markConnectionAsReconnecting(meetingCode, userId, sessionId);
            if (transition != MeetingPresenceService.PresenceTransition.RECONNECTING) {
                return;
            }

            // Bắn thông báo cho những người còn lại biết để hiển thị icon "Đang kết nối lại..."
            SignalingMessage disconnectMsg = SignalingMessage.builder()
                    .category(MessageCategory.PRESENCE)
                    .type(PresenceType.RECONNECTING.name())
                    .senderId(userId)
                    .meetingCode(meetingCode)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/meeting." + meetingCode, disconnectMsg);
        }
    }
}
