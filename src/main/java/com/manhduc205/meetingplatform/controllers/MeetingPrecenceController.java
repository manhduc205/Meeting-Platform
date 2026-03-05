package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.SignalingMessage;
import com.manhduc205.meetingplatform.enums.*;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;

import static com.manhduc205.meetingplatform.enums.MessageCategory.*;

@Controller
@RequiredArgsConstructor
public class MeetingPrecenceController {
    private final SimpMessagingTemplate messagingTemplate;
    private final MeetingPresenceService presenceService;

    @MessageMapping("/meeting.signal")
    public void processSignaling(@Payload SignalingMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String roomTopic = "/topic/meeting." + message.getMeetingCode();
        message.setTimestamp(LocalDateTime.now());

        switch (message.getCategory()) {
            case PRESENCE -> {
                headerAccessor.getSessionAttributes().put("meetingCode", message.getMeetingCode());
                headerAccessor.getSessionAttributes().put("userId", message.getSenderId());

                List<SignalingMessage> responses = presenceService.handlePresenceUpdate(message);
                responses.forEach(res -> messagingTemplate.convertAndSend(roomTopic, res));
            }
            // dùng cho chat/whiteboard hoặc lệnh chuyển đổi SFU mà cần Broadcast đến tất cả thành viên
            case SIGNALING -> messagingTemplate.convertAndSendToUser(
                    message.getTargetId(), "/queue/signaling", message);

            case ACTION -> messagingTemplate.convertAndSend(roomTopic, message);
        }
    }
}