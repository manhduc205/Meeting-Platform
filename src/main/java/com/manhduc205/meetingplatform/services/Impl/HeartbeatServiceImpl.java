package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 những người đã ngắt kết nối đột ngột sau 70s thì kick ra
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatServiceImpl implements com.manhduc205.meetingplatform.services.HeartbeatService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeetingPresenceService presenceService;

    // Prefix cho heartbeat tracking
    private static final String HEARTBEAT_PREFIX = "heartbeat:";
    private static final String PENDING_KEY_PREFIX = "pending:room:";

    // TTL cho heartbeat key (65s = 30s client ping + 35s buffer)
    private static final long HEARTBEAT_TTL_SECONDS = 65;

    @Override
    public void updateHeartbeat(String meetingCode, String userId) {
        try {
            String heartbeatKey = HEARTBEAT_PREFIX + meetingCode + ":" + userId;
            // Set/update key với TTL = 65s
            redisTemplate.opsForValue().set(
                    heartbeatKey,
                    String.valueOf(System.currentTimeMillis()),
                    HEARTBEAT_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.error("❌ Error updating heartbeat for user [{}]", userId, e);
        }
    }

    @Scheduled(fixedDelay = 70000, initialDelay = 10000) // 70s = 65s TTL + 5s buffer
    public void cleanupExpiredHeartbeats() {
        try {
            log.info("🧹 Starting heartbeat cleanup scan...");

            // Sử dụng SCAN thay vì KEYS để tránh block Redis
            // SCAN dùng cursor-based iteration (O(1) per call)
            // trong khi KEYS là O(N) - quét toàn bộ database
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(PENDING_KEY_PREFIX + "*")
                    .count(100)
                    .build();

            List<String> pendingKeysList = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    pendingKeysList.add(cursor.next());
                }
            }

            if (pendingKeysList.isEmpty()) {
                return;
            }

            log.debug(" Found {} pending reconnection keys to check", pendingKeysList.size());

            for (String pendingKey : pendingKeysList) {
                try {
                    String[] parts = pendingKey.split(":");
                    if (parts.length < 4) {
                        log.warn(" Invalid pending key format: {}", pendingKey);
                        continue;
                    }

                    String meetingCode = parts[2];
                    String userId = parts[3];

                    // Check if user has reconnected (heartbeat key exists)
                    String heartbeatKey = HEARTBEAT_PREFIX + meetingCode + ":" + userId;
                    boolean hasHeartbeat = Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey));

                    if (!hasHeartbeat) {
                        presenceService.removeOnlineUser(meetingCode, userId);
                        redisTemplate.delete(pendingKey);
                    }
                } catch (Exception e) {
                }
            }

        } catch (Exception e) {
        }
    }
}

