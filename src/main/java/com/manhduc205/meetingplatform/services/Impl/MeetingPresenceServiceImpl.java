package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.request.SignalingMessage;
import com.manhduc205.meetingplatform.enums.MessageCategory;
import com.manhduc205.meetingplatform.enums.PresenceType;
import com.manhduc205.meetingplatform.enums.SignalingType;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingPresenceServiceImpl implements MeetingPresenceService {
    private final RedisTemplate<String, Object> redisTemplate;

    // Prefix để dễ quản lý key trong Redis (VD: room:abc-def-ghi:users)
    private static final String ROOM_KEY_PREFIX = "room:";
    private static final String ROOM_KEY_SUFFIX = ":users";
    private static final String PENDING_KEY_PREFIX = "pending:room:";
    @Override
    public void addOnlineUser(String meetingCode, String userId) {
        String key = ROOM_KEY_PREFIX + meetingCode + ROOM_KEY_SUFFIX;
        redisTemplate.opsForSet().add(key, userId);
    }

    @Override
    public void removeOnlineUser(String meetingCode, String userId) {
        String key = ROOM_KEY_PREFIX + meetingCode + ROOM_KEY_SUFFIX;
        redisTemplate.opsForSet().remove(key, userId);
    }

    @Override
    public Set<Object> getOnlineUsers(String meetingCode) {
        String key = ROOM_KEY_PREFIX + meetingCode + ROOM_KEY_SUFFIX;
        Set<Object> members = redisTemplate.opsForSet().members(key);
        return Optional.ofNullable(members).orElse(Collections.emptySet());

    }
    @Override
    public void markUserAsReconnecting(String meetingCode, String userId){
        String pendingKey = PENDING_KEY_PREFIX + meetingCode;
        // Xóa khỏi danh sách active ngay để không tính vào ngưỡng Hybrid
        this.removeOnlineUser(meetingCode, userId);
        redisTemplate.opsForValue().set(pendingKey, "WAITING_FOR_RECONNECT", 60, java.util.concurrent.TimeUnit.SECONDS);
        log.info("User [{}] được ân hạn 60s để kết nối lại phòng [{}].", userId, meetingCode);
    }
    @Override
    public List<SignalingMessage> handlePresenceUpdate(SignalingMessage message) {
        String mCode = message.getMeetingCode();
        String uId = message.getSenderId();
        PresenceType pType = PresenceType.valueOf(message.getType());

        List<SignalingMessage> responses = new ArrayList<>();
        // Luôn trả về tin nhắn gốc để thông báo trạng thái User hiện tại
        responses.add(message);

        if (PresenceType.JOIN.equals(pType)) {
            String pendingKey = PENDING_KEY_PREFIX + mCode + ":" + uId;

            // check xem user có đang reconnecting hay không (tức là có key Pending tồn tại
            Boolean isReconnecting = redisTemplate.hasKey(pendingKey);

            if (Boolean.TRUE.equals(isReconnecting)) {
                // Xóa key Pending
                redisTemplate.delete(pendingKey);
                message.setType("RECONNECTED");
                log.info("User [{}] đã RECONNECT thành công vào phòng [{}].", uId, mCode);
            }
            this.addOnlineUser(mCode, uId);
            // Sau khi thêm user vào online, gửi thêm 1 tin nhắn chứa danh sách user hiện tại để đồng bộ cho client mới vào
            Set<Object> currentUsers = this.getOnlineUsers(mCode);
            SignalingMessage syncMsg = SignalingMessage.builder()
                    .category(MessageCategory.PRESENCE)
                    .type("USER_LIST_SYNC")
                    .targetId(uId)
                    .payload(currentUsers)
                    .build();
            responses.add(syncMsg);
            // Kiểm tra ngưỡng để tự động tạo lệnh chuyển đổi SFU
            if (this.shouldSwitchToSfu(mCode)) {
                responses.add(SignalingMessage.builder()
                        .category(MessageCategory.SIGNALING)
                        .type(SignalingType.SWITCH_TO_SFU.name())
                        .meetingCode(mCode)
                        .timestamp(LocalDateTime.now())
                        .build());
            }
        } else if (PresenceType.LEAVE.equals(pType)) {
            this.removeOnlineUser(mCode, uId);
        }

        return responses;
    }
    @Override
    public boolean shouldSwitchToSfu(String meetingCode) {
        String key = ROOM_KEY_PREFIX + meetingCode + ROOM_KEY_SUFFIX;
        Long onlineCount = redisTemplate.opsForSet().size(key);

        // Nếu số người >= 3 thì chuyển sang chế độ SFU
        boolean isSfu = onlineCount != null && onlineCount >= 3;
        if (isSfu) {
            log.warn("Phòng [{}] đạt ngưỡng SFU ({} người). Kích hoạt lệnh chuyển đổi!", meetingCode, onlineCount);
        }
        return isSfu;
    }

}
