package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.mappers.ParticipantMapper;
import com.manhduc205.meetingplatform.dtos.response.ActiveParticipantsResponse;
import com.manhduc205.meetingplatform.dtos.response.JoinMeetingResponse;
import com.manhduc205.meetingplatform.dtos.response.ParticipantDto;
import com.manhduc205.meetingplatform.enums.ParticipantStatus;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.UserEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service Implementation cho Participant Management & Join Logic
 *
 * ✅ ENTERPRISE OPTIMIZATIONS:
 * 1. Redis ZSet thay vì Set → tránh UX flicker (maintain insertion order)
 * 2. TTL expiration cho tất cả participant keys → prevent memory leak
 * 3. Debounce knock notifications → prevent spam WebSocket messages
 * 4. Rate limiting check (ở API level) → prevent joinMeeting spam
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingParticipantServiceImpl implements MeetingParticipantService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final ParticipantMapper participantMapper;
    private final MeetingPresenceService presenceService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    // Redis keys prefix
    private static final String ACTIVE_PARTICIPANTS_PREFIX = "active:participants:";
    private static final String WAITING_PARTICIPANTS_PREFIX = "waiting:participants:";
    private static final String PENDING_KNOCK_PREFIX = "pending:knock:";

    private static final int DISPLAY_LIMIT = 10;  // Limit để hiển thị chi tiết

    private static final long ACTIVE_PARTICIPANTS_TTL_HOURS = 12;  // Session timeout
    private static final long WAITING_PARTICIPANTS_TTL_MINUTES = 30;  // Knock request timeout
    private static final long PENDING_KNOCK_TTL_SECONDS = 30;  // Debounce window

    @Override
    @Transactional(readOnly = true)
    public ActiveParticipantsResponse getActiveParticipants(String meetingCode) {
        log.info("🔍 ServiceImpl: Lấy danh sách participant đang họp từ phòng: {}", meetingCode);

        // Verify meeting exists
        meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp: " + meetingCode));

        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        Set<Object> activeUserIds = redisTemplate.opsForZSet().range(activeKey, 0, DISPLAY_LIMIT - 1);

        if (activeUserIds == null || activeUserIds.isEmpty()) {
            log.info("ℹ️ ServiceImpl: Không có participant nào trong phòng {}", meetingCode);
            return ActiveParticipantsResponse.builder()
                    .totalCount(0)
                    .participants(Collections.emptyList())
                    .displayText("No one is here yet")
                    .build();
        }

        List<String> userIdList = activeUserIds.stream()
                .map(Object::toString)
                .toList();

        List<UserEntity> users = userRepository.findAllById(userIdList);

        Map<String, UserEntity> userMapById = new HashMap<>(users.size());
        for (UserEntity user : users) {
            userMapById.put(user.getId(), user);
        }
        log.debug("✅ Batch query retrieved {} users from database in 1 query", users.size());

        List<ParticipantDto> participants = new ArrayList<>(userIdList.size());
        for (String userId : userIdList) {
            UserEntity user = userMapById.get(userId);
            if (user != null) {
                ParticipantDto dto = participantMapper.toParticipantDto(user);
                dto.setStatus(ParticipantStatus.ACTIVE.name());
                participants.add(dto);
            }
        }

        Long totalCount = redisTemplate.opsForZSet().size(activeKey);
        String displayText = buildDisplayText(participants, totalCount != null ? totalCount.intValue() : 0);

        return ActiveParticipantsResponse.builder()
                .totalCount(totalCount != null ? totalCount.intValue() : 0)
                .participants(participants)
                .displayText(displayText)
                .build();
    }

    @Override
    @Transactional
    public JoinMeetingResponse joinMeeting(String meetingCode, String userId, String meetingPassword) {
        log.info("ServiceImpl: User [{}] cố gắng join phòng [{}]", userId, meetingCode);

        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp: " + meetingCode));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user: " + userId));

        if (meeting.getMeetingPassword() != null && !meeting.getMeetingPassword().isEmpty()
                && (meetingPassword == null || !meetingPassword.equals(meeting.getMeetingPassword()))) {
            log.warn("  User [{}] nhập password sai cho phòng [{}]", userId, meetingCode);
            throw new SecurityException("Password không chính xác!");
        }

        boolean isHost = meeting.getHostId().equals(userId);
        boolean isWaitingRoomEnabled = meeting.getIsWaitingRoomEnabled() != null && meeting.getIsWaitingRoomEnabled();

        if (isHost) {
            log.info("✅ Host [{}] vào phòng [{}]", userId, meetingCode);
            addActiveParticipant(meetingCode, userId);
            return JoinMeetingResponse.builder()
                    .meetingCode(meetingCode)
                    .userId(userId)
                    .status(ParticipantStatus.APPROVED.name())
                    .message("Welcome Host! You are now in the meeting")
                    .build();
        }

        if (!isWaitingRoomEnabled) {
            log.info("✅ Guest [{}] vào phòng [{}] (waiting room disabled)", userId, meetingCode);
            addActiveParticipant(meetingCode, userId);
            return JoinMeetingResponse.builder()
                    .meetingCode(meetingCode)
                    .userId(userId)
                    .status(ParticipantStatus.APPROVED.name())
                    .message("You have joined the meeting!")
                    .build();
        }

        // User là Guest & Waiting Room bật -> chờ duyệt
        log.info("⏳ Guest [{}] gõ cửa phòng [{}] (waiting room enabled)", userId, meetingCode);
        addWaitingParticipant(meetingCode, userId);

        notifyHostAboutKnock(meetingCode, user);

        return JoinMeetingResponse.builder()
                .meetingCode(meetingCode)
                .userId(userId)
                .status(ParticipantStatus.WAITING.name())
                .message("Your request has been sent to the host. Please wait for approval.")
                .build();
    }

    private void addActiveParticipant(String meetingCode, String userId) {
        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(activeKey, userId, score);

        redisTemplate.expire(activeKey, ACTIVE_PARTICIPANTS_TTL_HOURS, TimeUnit.HOURS);

        presenceService.addOnlineUser(meetingCode, userId);
        log.debug("✅ Added active participant [{}] to room [{}] with TTL={} hours",
                userId, meetingCode, ACTIVE_PARTICIPANTS_TTL_HOURS);
    }
    private void addWaitingParticipant(String meetingCode, String userId) {
        String waitingKey = WAITING_PARTICIPANTS_PREFIX + meetingCode;
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(waitingKey, userId, score);

        redisTemplate.expire(waitingKey, WAITING_PARTICIPANTS_TTL_MINUTES, TimeUnit.MINUTES);

        log.debug(" Added waiting participant [{}] to room [{}] with TTL={} minutes",
                userId, meetingCode, WAITING_PARTICIPANTS_TTL_MINUTES);
    }

    /** Gửi WebSocket notification đến Host về knock request
     */
    private void notifyHostAboutKnock(String meetingCode, UserEntity user) {
        try {
            String userId = user.getId();
            String pendingKnockKey = PENDING_KNOCK_PREFIX + meetingCode + ":" + userId;

            //  Kiểm tra xem user này đã knock gần đây chưa
            Boolean alreadyKnocking = redisTemplate.hasKey(pendingKnockKey);

            if (Boolean.TRUE.equals(alreadyKnocking)) {
                return;
            }

            redisTemplate.opsForValue().set(pendingKnockKey, "KNOCKING", PENDING_KNOCK_TTL_SECONDS, TimeUnit.SECONDS);

            // Gửi notification
            String userName = user.getFullName() != null ? user.getFullName() : user.getEmail();
            String hostNotificationMessage = String.format("%s đang xin vào phòng", userName);

            // Gửi thông báo đến host qua WebSocket topic riêng
            messagingTemplate.convertAndSend(
                    "/topic/meeting." + meetingCode + ".host-notifications",
                    Map.of(
                            "type", "KNOCK_REQUEST",
                            "userId", userId,
                            "userName", userName,
                            "userEmail", user.getEmail(),
                            "avatarUrl", user.getAvatarUrl(),
                            "message", hostNotificationMessage
                    )
            );
            log.info("✅ Sent knock notification for user [{}] to room [{}]", userId, meetingCode);
        } catch (Exception e) {
            log.error("❌ Error sending knock notification for user [{}]", user.getId(), e);
        }
    }

    private String buildDisplayText(List<ParticipantDto> participants, int totalCount) {
        if (participants.isEmpty()) {
            return "No one is here yet";
        }

        StringBuilder nameList = new StringBuilder();
        int displayCount = Math.min(2, participants.size());

        for (int i = 0; i < displayCount; i++) {
            if (i > 0) {
                nameList.append(", ");
            }
            String name = participants.get(i).getFirstName() != null
                    ? participants.get(i).getFirstName()
                    : "Someone";
            nameList.append(name);
        }

        int othersCount = totalCount - displayCount;
        if (othersCount > 0) {
            return String.format("%s, and %d others are already here", nameList, othersCount);
        }

        String verb = displayCount > 1 ? "are" : "is";
        return nameList.append(" ").append(verb).append(" already here").toString();
    }
}

