package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.enums.MessageCategory;
import com.manhduc205.meetingplatform.enums.PresenceType;
import com.manhduc205.meetingplatform.models.dtos.request.SignalingMessage;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tracks real-time presence per WebSocket session. The active-participants ZSet
 * remains the aggregate view consumed by participant APIs; connection hashes
 * decide when that aggregate is added, retained, or removed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingPresenceServiceImpl implements MeetingPresenceService {
    private static final String ACTIVE_PARTICIPANTS_PREFIX = "active:participants:";
    private static final String ADMITTED_PARTICIPANTS_PREFIX = "admitted:participants:";
    private static final String CONNECTION_PREFIX = "presence:connection:";
    private static final String USER_CONNECTIONS_PREFIX = "presence:room:";
    private static final String USER_CONNECTIONS_SUFFIX = ":connections";
    private static final String RATE_LIMIT_PREFIX = "rate_limit:reaction:";

    private static final String FIELD_ROOM_ID = "roomId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_LAST_SEEN = "lastSeen";
    private static final String FIELD_DEADLINE = "deadline";

    private static final String ACTIVE = "ACTIVE";
    private static final String RECONNECTING = "RECONNECTING";
    private static final long SUSPECT_AFTER_MS = TimeUnit.SECONDS.toMillis(65);
    private static final long RECONNECT_GRACE_MS = TimeUnit.SECONDS.toMillis(30);

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public Set<String> getOnlineUsers(String meetingCode) {
        Set<String> users = redisTemplate.opsForZSet().range(activeParticipantsKey(meetingCode), 0, -1);
        return users == null ? Collections.emptySet() : users;
    }

    @Override
    public List<SignalingMessage> handlePresenceUpdate(SignalingMessage message, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new SecurityException("WebSocket session không hợp lệ");
        }

        PresenceType type = PresenceType.valueOf(message.getType());
        String meetingCode = message.getMeetingCode();
        String userId = message.getSenderId();
        List<SignalingMessage> responses = new ArrayList<>();

        if (type == PresenceType.JOIN) {
            PresenceTransition transition = registerConnection(meetingCode, userId, sessionId);
            if (transition == PresenceTransition.JOINED || transition == PresenceTransition.RECONNECTED) {
                if (transition == PresenceTransition.RECONNECTED) {
                    message.setType(PresenceType.RECONNECTED.name());
                }
                responses.add(message);
            }
            responses.add(SignalingMessage.builder()
                    .category(MessageCategory.PRESENCE)
                    .type(PresenceType.USER_LIST_SYNC.name())
                    .meetingCode(meetingCode)
                    .targetId(userId)
                    .payload(getOnlineUsers(meetingCode))
                    .build());
            return responses;
        }

        if (type == PresenceType.LEAVE) {
            PresenceTransition transition = leaveConnection(meetingCode, userId, sessionId);
            if (transition == PresenceTransition.OFFLINE) {
                responses.add(message);
            } else if (transition == PresenceTransition.RECONNECTING) {
                message.setType(PresenceType.RECONNECTING.name());
                responses.add(message);
            }
            return responses;
        }

        throw new IllegalArgumentException("Presence event không được hỗ trợ: " + type);
    }

    @Override
    public PresenceTransition markConnectionAsReconnecting(String meetingCode, String userId, String sessionId) {
        if (!belongsToConnection(meetingCode, userId, sessionId)) return PresenceTransition.UNCHANGED;

        long now = System.currentTimeMillis();
        redisTemplate.opsForHash().put(connectionKey(sessionId), FIELD_STATE, RECONNECTING);
        redisTemplate.opsForHash().put(connectionKey(sessionId), FIELD_DEADLINE,
                String.valueOf(now + RECONNECT_GRACE_MS));

        return hasConnectionWithState(meetingCode, userId, ACTIVE, sessionId)
                ? PresenceTransition.UNCHANGED
                : PresenceTransition.RECONNECTING;
    }

    @Override
    public boolean refreshConnection(String meetingCode, String userId, String sessionId) {
        if (!belongsToConnection(meetingCode, userId, sessionId)) return false;

        String previousState = value(
                redisTemplate.opsForHash().entries(connectionKey(sessionId)), FIELD_STATE);
        boolean shouldBroadcastReconnect = RECONNECTING.equals(previousState)
                && !hasConnectionWithState(meetingCode, userId, ACTIVE, sessionId);

        redisTemplate.opsForHash().put(connectionKey(sessionId), FIELD_LAST_SEEN,
                String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().put(connectionKey(sessionId), FIELD_STATE, ACTIVE);
        redisTemplate.opsForHash().delete(connectionKey(sessionId), FIELD_DEADLINE);
        if (shouldBroadcastReconnect) {
            broadcastPresence(meetingCode, userId, PresenceType.RECONNECTED.name());
        }
        return true;
    }

    @Override
    public void cleanupStaleConnections() {
        long now = System.currentTimeMillis();
        ScanOptions options = ScanOptions.scanOptions().match(CONNECTION_PREFIX + "*").count(100).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) processConnectionForCleanup(cursor.next(), now);
        } catch (Exception exception) {
            log.warn("Không thể dọn kết nối presence cũ: {}", exception.getMessage());
        }
    }

    @Override
    public void removeAllUserConnections(String meetingCode, String userId) {
        for (String sessionId : liveSessionIds(meetingCode, userId)) {
            redisTemplate.delete(connectionKey(sessionId));
        }
        redisTemplate.delete(userConnectionsKey(meetingCode, userId));
        removeAggregateUser(meetingCode, userId);
    }

    @Override
    public void handleActionMessage(SignalingMessage message) {
        if ("REACTION".equals(message.getType())) {
            String key = RATE_LIMIT_PREFIX + message.getMeetingCode() + ":" + message.getSenderId();
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) redisTemplate.expire(key, 2, TimeUnit.SECONDS);
            if (count != null && count > 5) return;
        }
        messagingTemplate.convertAndSend("/topic/meeting." + message.getMeetingCode(), message);
    }

    private PresenceTransition registerConnection(String meetingCode, String userId, String sessionId) {
        if (!Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(
                ADMITTED_PARTICIPANTS_PREFIX + meetingCode, userId))) {
            throw new SecurityException("Người dùng chưa được duyệt vào cuộc họp");
        }

        boolean wasReconnecting = hasConnectionWithState(meetingCode, userId, RECONNECTING, null)
                && !hasConnectionWithState(meetingCode, userId, ACTIVE, null);
        long now = System.currentTimeMillis();

        redisTemplate.opsForHash().putAll(connectionKey(sessionId), Map.of(
                FIELD_ROOM_ID, meetingCode,
                FIELD_USER_ID, userId,
                FIELD_STATE, ACTIVE,
                FIELD_LAST_SEEN, String.valueOf(now)
        ));
        redisTemplate.opsForHash().delete(connectionKey(sessionId), FIELD_DEADLINE);
        redisTemplate.opsForSet().add(userConnectionsKey(meetingCode, userId), sessionId);
        redisTemplate.opsForZSet().add(activeParticipantsKey(meetingCode), userId, now);
        redisTemplate.expire(activeParticipantsKey(meetingCode), 12, TimeUnit.HOURS);

        return wasReconnecting ? PresenceTransition.RECONNECTED : PresenceTransition.JOINED;
    }

    private PresenceTransition leaveConnection(String meetingCode, String userId, String sessionId) {
        if (!belongsToConnection(meetingCode, userId, sessionId)) return PresenceTransition.UNCHANGED;
        removeConnection(meetingCode, userId, sessionId);

        if (!hasAnyConnection(meetingCode, userId)) {
            removeAggregateUser(meetingCode, userId);
            return PresenceTransition.OFFLINE;
        }
        return hasConnectionWithState(meetingCode, userId, ACTIVE, null)
                ? PresenceTransition.UNCHANGED
                : PresenceTransition.RECONNECTING;
    }

    private void processConnectionForCleanup(String key, long now) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
        if (values.isEmpty()) return;

        String meetingCode = value(values, FIELD_ROOM_ID);
        String userId = value(values, FIELD_USER_ID);
        String state = value(values, FIELD_STATE);
        String sessionId = key.substring(CONNECTION_PREFIX.length());
        if (meetingCode == null || userId == null || state == null) {
            redisTemplate.delete(key);
            return;
        }

        if (ACTIVE.equals(state) && elapsed(now, value(values, FIELD_LAST_SEEN)) >= SUSPECT_AFTER_MS) {
            redisTemplate.opsForHash().put(key, FIELD_STATE, RECONNECTING);
            redisTemplate.opsForHash().put(key, FIELD_DEADLINE, String.valueOf(now + RECONNECT_GRACE_MS));
            if (!hasConnectionWithState(meetingCode, userId, ACTIVE, sessionId)) {
                broadcastPresence(meetingCode, userId, PresenceType.RECONNECTING.name());
            }
            return;
        }

        if (RECONNECTING.equals(state) && now >= asLong(value(values, FIELD_DEADLINE))) {
            removeConnection(meetingCode, userId, sessionId);
            if (!hasAnyConnection(meetingCode, userId)) {
                removeAggregateUser(meetingCode, userId);
                broadcastPresence(meetingCode, userId, PresenceType.LEAVE.name());
            }
        }
    }

    private boolean belongsToConnection(String meetingCode, String userId, String sessionId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(connectionKey(sessionId));
        return meetingCode.equals(value(values, FIELD_ROOM_ID)) && userId.equals(value(values, FIELD_USER_ID));
    }

    private boolean hasAnyConnection(String meetingCode, String userId) {
        return !liveSessionIds(meetingCode, userId).isEmpty();
    }

    private boolean hasConnectionWithState(String meetingCode, String userId, String expectedState, String excludedSessionId) {
        for (String sessionId : liveSessionIds(meetingCode, userId)) {
            if (sessionId.equals(excludedSessionId)) continue;
            String state = value(redisTemplate.opsForHash().entries(connectionKey(sessionId)), FIELD_STATE);
            if (expectedState.equals(state)) return true;
        }
        return false;
    }

    private Set<String> liveSessionIds(String meetingCode, String userId) {
        String key = userConnectionsKey(meetingCode, userId);
        Set<String> stored = redisTemplate.opsForSet().members(key);
        if (stored == null || stored.isEmpty()) return Collections.emptySet();

        Set<String> sessionIds = new HashSet<>(stored);
        sessionIds.removeIf(sessionId -> {
            boolean missing = Boolean.FALSE.equals(redisTemplate.hasKey(connectionKey(sessionId)));
            if (missing) redisTemplate.opsForSet().remove(key, sessionId);
            return missing;
        });
        if (sessionIds.isEmpty()) redisTemplate.delete(key);
        return sessionIds;
    }

    private void removeConnection(String meetingCode, String userId, String sessionId) {
        redisTemplate.delete(connectionKey(sessionId));
        String key = userConnectionsKey(meetingCode, userId);
        redisTemplate.opsForSet().remove(key, sessionId);
        Long remaining = redisTemplate.opsForSet().size(key);
        if (remaining == null || remaining == 0) redisTemplate.delete(key);
    }

    private void removeAggregateUser(String meetingCode, String userId) {
        redisTemplate.opsForZSet().remove(activeParticipantsKey(meetingCode), userId);
    }

    private void broadcastPresence(String meetingCode, String userId, String type) {
        messagingTemplate.convertAndSend("/topic/meeting." + meetingCode,
                SignalingMessage.builder()
                        .category(MessageCategory.PRESENCE)
                        .type(type)
                        .senderId(userId)
                        .meetingCode(meetingCode)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    private String activeParticipantsKey(String meetingCode) {
        return ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
    }

    private String connectionKey(String sessionId) {
        return CONNECTION_PREFIX + sessionId;
    }

    private String userConnectionsKey(String meetingCode, String userId) {
        return USER_CONNECTIONS_PREFIX + meetingCode + ":user:" + userId + USER_CONNECTIONS_SUFFIX;
    }

    private String value(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? null : value.toString();
    }

    private long elapsed(long now, String timestamp) {
        return now - asLong(timestamp);
    }

    private long asLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
