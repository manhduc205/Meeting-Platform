package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingPresenceServiceImpl implements MeetingPresenceService {
    private final RedisTemplate<String, Object> redisTemplate;

    // Prefix để dễ quản lý key trong Redis (VD: room:abc-def-ghi:users)
    private static final String ROOM_KEY_PREFIX = "room:";
    private static final String ROOM_KEY_SUFFIX = ":users";
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
