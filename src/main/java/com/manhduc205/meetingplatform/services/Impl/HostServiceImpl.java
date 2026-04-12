package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.exceptions.ResourceNotFoundException;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.UserEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.HostService;
import com.manhduc205.meetingplatform.services.UserIdCacheService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HostServiceImpl implements HostService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final UserIdCacheService userIdCacheService;
    private final SimpMessagingTemplate messagingTemplate;

    private MeetingEntity getValidatedMeeting(String meetingCode) {
        String internalUserId = UserContext.getUserId();
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        if (!meeting.getHostId().equals(internalUserId)) {
            log.warn("Cảnh báo bảo mật: User [{}] cố gắng dùng quyền Host tại phòng [{}]", internalUserId, meetingCode);
            throw new SecurityException("Chỉ Host mới có quyền thực hiện hành động này");
        }
        return meeting;
    }

    @Override
    @Transactional
    public void updateSecuritySetting(String meetingCode, String type, boolean enabled) {
        MeetingEntity meeting = getValidatedMeeting(meetingCode);

        switch (type) {
            case "LOCK_MEETING":
                meeting.setIsLocked(enabled);
                break;
            case "WAITING_ROOM":
                meeting.setIsWaitingRoomEnabled(enabled);
                break;
            case "DISABLE_SCREEN_SHARE":
                meeting.setIsScreenShareDisabled(enabled);
                break;
        }
        meetingRepository.save(meeting);

        sendHostCommand(meetingCode, "SETTING_CHANGED", Map.of("type", type, "enabled", enabled));
    }

    @Override
    public void muteAll(String meetingCode) {
        getValidatedMeeting(meetingCode);
        sendHostCommand(meetingCode, "MUTE_ALL", null);
    }

    @Override
    public void kickUser(String meetingCode, String targetUserId) {
        getValidatedMeeting(meetingCode);
        sendHostCommand(meetingCode, "KICK_PARTICIPANT", Map.of("targetId", targetUserId));
    }

    private void sendHostCommand(String meetingCode, String action, Object payload) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", action);
        body.put("data", payload);
        body.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/meeting." + meetingCode + ".host-commands", body);
    }
}