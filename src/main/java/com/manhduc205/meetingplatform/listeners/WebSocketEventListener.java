package com.manhduc205.meetingplatform.listeners;

import com.manhduc205.meetingplatform.dtos.request.SignalingMessage;
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

        String meetingCode = (String) headers.getSessionAttributes().get("meetingCode");

        if (userId != null && meetingCode != null) {
            log.warn("Phát hiện User [{}] rớt mạng khỏi phòng [{}]", userId, meetingCode);

            // Gọi Service để chuyển trạng thái sang RECONNECTING
            presenceService.markUserAsReconnecting(meetingCode, userId);

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