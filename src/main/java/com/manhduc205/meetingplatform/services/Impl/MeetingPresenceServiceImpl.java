package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.request.SignalingMessage;
import com.manhduc205.meetingplatform.enums.MessageCategory;
import com.manhduc205.meetingplatform.enums.PresenceType;
import com.manhduc205.meetingplatform.enums.SignalingType;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingPresenceServiceImpl implements MeetingPresenceService {
    private final RedisTemplate<String, Object> redisTemplate;
     private final StringRedisTemplate stringRedisTemplate;
     private final SimpMessagingTemplate messagingTemplate;
    // room:abc-def-ghi:users
    // ZSet sử dụng timestamp làm score để maintain insertion order
    private static final String ROOM_KEY_PREFIX = "room:";
    private static final String ROOM_KEY_SUFFIX = ":users";
    private static final String PENDING_KEY_PREFIX = "pending:room:";

    private static final long ROOM_KEY_TTL_HOURS = 12;

    @Override
    public void addOnlineUser(String meetingCode, String userId) {
        String key = ROOM_KEY_PREFIX + meetingCode + ROOM_KEY_SUFFIX;
        // Dùng ZSet với score = current timestamp (ms)
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, userId, score);

        redisTemplate.expire(key, ROOM_KEY_TTL_HOURS, TimeUnit.HOURS);
    }
    @Override
    public void removeOnlineUser(String meetingCode, String userId) {
        String key = ROOM_KEY_PREFIX + meetingCode + ROOM_KEY_SUFFIX;
        redisTemplate.opsForZSet().remove(key, userId);
        log.debug("✅ Removed online user [{}] from room [{}]", userId, meetingCode);
    }

    @Override
    public Set<Object> getOnlineUsers(String meetingCode) {
        String key = ROOM_KEY_PREFIX + meetingCode + ROOM_KEY_SUFFIX;
        // Lấy tất cả members từ ZSet, sorted by score (timestamp) ascending (oldest first)
        Set<Object> members = redisTemplate.opsForZSet().range(key, 0, -1);
        return Optional.ofNullable(members).orElse(Collections.emptySet());

    }
    @Override
    public void markUserAsReconnecting(String meetingCode, String userId){
        String pendingKey = PENDING_KEY_PREFIX + meetingCode + ":" + userId;
        this.removeOnlineUser(meetingCode, userId);
        redisTemplate.opsForValue().set(pendingKey, "WAITING_FOR_RECONNECT", 60, TimeUnit.SECONDS);
    }



    public void handleActionMessage(SignalingMessage message) {
        if ("REACTION".equals(message.getType())) {
            String rateLimitKey = "rate_limit:reaction:" + message.getMeetingCode() + ":" + message.getSenderId();

            Long count = stringRedisTemplate.opsForValue().increment(rateLimitKey);

            if (count != null && count == 1) {
                // Reset bộ đếm sau 2 giây
                stringRedisTemplate.expire(rateLimitKey, 2, TimeUnit.SECONDS);
            }

            if (count != null && count > 5) {
                return;
            }
        }

        String roomTopic = "/topic/meeting." + message.getMeetingCode();
        messagingTemplate.convertAndSend(roomTopic, message);
    }

    @Override
    public List<SignalingMessage> handlePresenceUpdate(SignalingMessage message) {
        String mCode = message.getMeetingCode();
        String uId = message.getSenderId();
        PresenceType pType = PresenceType.valueOf(message.getType());

        List<SignalingMessage> responses = new ArrayList<>();
        responses.add(message);

        if (PresenceType.JOIN.equals(pType)) {
            String pendingKey = PENDING_KEY_PREFIX + mCode + ":" + uId;

            Boolean isReconnecting = redisTemplate.hasKey(pendingKey);

            if (isReconnecting) {
                redisTemplate.delete(pendingKey);
                message.setType("RECONNECTED");
                log.info(" User [{}] đã RECONNECT thành công vào phòng [{}].", uId, mCode);
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
//            if (this.shouldSwitchToSfu(mCode)) {
//                responses.add(SignalingMessage.builder()
//                        .category(MessageCategory.SIGNALING)
//                        .type(SignalingType.SWITCH_TO_SFU.name())
//                        .meetingCode(mCode)
//                        .timestamp(LocalDateTime.now())
//                        .build());
//            }
        } else if (PresenceType.LEAVE.equals(pType)) {
            this.removeOnlineUser(mCode, uId);
        }

        return responses;
    }
//    @Override
//    public boolean shouldSwitchToSfu(String meetingCode) {
//        String key = ROOM_KEY_PREFIX + meetingCode + ROOM_KEY_SUFFIX;
//        Long onlineCount = redisTemplate.opsForZSet().size(key);
//
//        // Nếu số người >= 3 thì chuyển sang chế độ SFU
//        boolean isSfu = onlineCount != null && onlineCount >= 0;
//        if (isSfu) {
//            log.warn("Phòng [{}] đạt ngưỡng SFU ({} người). Kích hoạt lệnh chuyển đổi!", meetingCode, onlineCount);
//        }
//        return isSfu;
//    }

}
