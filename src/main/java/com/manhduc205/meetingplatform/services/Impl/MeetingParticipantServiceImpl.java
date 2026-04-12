package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.mappers.ParticipantMapper;
import com.manhduc205.meetingplatform.dtos.response.ActiveParticipantsResponse;
import com.manhduc205.meetingplatform.dtos.response.JoinMeetingResponse;
import com.manhduc205.meetingplatform.dtos.response.ParticipantDto;
import com.manhduc205.meetingplatform.dtos.response.RaisedHandResponse;
import com.manhduc205.meetingplatform.enums.ParticipantStatus;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.UserEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import com.manhduc205.meetingplatform.services.UserIdCacheService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingParticipantServiceImpl implements MeetingParticipantService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final ParticipantMapper participantMapper;
    private final MeetingPresenceService presenceService;
    private final UserIdCacheService userIdCacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    // Redis keys prefix
    private static final String ACTIVE_PARTICIPANTS_PREFIX = "active:participants:";
    private static final String WAITING_PARTICIPANTS_PREFIX = "waiting:participants:";
    private static final String PENDING_KNOCK_PREFIX = "pending:knock:";
    private static final String RAISED_HANDS_PREFIX = "meeting:raised_hands:";
    private static final int DISPLAY_LIMIT = 10;

    private static final long ACTIVE_PARTICIPANTS_TTL_HOURS = 12;
    private static final long WAITING_PARTICIPANTS_TTL_MINUTES = 30;
    private static final long PENDING_KNOCK_TTL_SECONDS = 30;

    @Override
    @Transactional(readOnly = true)
    public ActiveParticipantsResponse getActiveParticipants(String meetingCode) {
        log.info(" ServiceImpl: Lấy danh sách participant đang họp từ phòng: {}", meetingCode);

        // Verify meeting exists
        meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp: " + meetingCode));

        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        Set<Object> activeUserIds = redisTemplate.opsForZSet().range(activeKey, 0, DISPLAY_LIMIT - 1);

        if (activeUserIds == null || activeUserIds.isEmpty()) {
            log.info(" ServiceImpl: Không có participant nào trong phòng {}", meetingCode);
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
        log.debug(" Batch query retrieved {} users from database in 1 query", users.size());

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
    @Transactional(readOnly = true)
    public List<ParticipantDto> getAllParticipants(String meetingCode) {
        log.info("Lấy FULL danh sách cho Sidebar phòng: {}", meetingCode);

        // 1. Tìm Meeting để xác định ai là Host
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp"));
        String hostId = meeting.getHostId();

        // 2. Lấy TOÀN BỘ User ID từ Redis (Truyền 0 đến -1 thay vì limit)
        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        Set<Object> activeUserIds = redisTemplate.opsForZSet().range(activeKey, 0, -1);

        if (activeUserIds == null || activeUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Lấy thêm danh sách đang giơ tay từ Redis để mapping trạng thái
        String raisedHandKey = RAISED_HANDS_PREFIX + meetingCode;
        Set<Object> raisedHands = redisTemplate.opsForZSet().range(raisedHandKey, 0, -1);
        Set<String> raisedHandIds = (raisedHands != null)
                ? raisedHands.stream().map(Object::toString).collect(Collectors.toSet())
                : Collections.emptySet();

        // 4. Query Batch Database
        List<String> userIdList = activeUserIds.stream().map(Object::toString).toList();
        List<UserEntity> users = userRepository.findAllById(userIdList);

        // 🔥 TỐI ƯU CẤU TRÚC DỮ LIỆU: HashMap Capacity (Chia 0.75 chống Rehash)
        int mapCapacity = (int) (users.size() / 0.75f) + 1;
        Map<String, UserEntity> userMapById = new HashMap<>(mapCapacity);
        for (UserEntity user : users) {
            userMapById.put(user.getId(), user);
        }

        // 5. Mapping DTO và đính kèm trạng thái
        List<ParticipantDto> participants = new ArrayList<>(userIdList.size());
        for (String userId : userIdList) {
            UserEntity user = userMapById.get(userId);
            if (user != null) {
                ParticipantDto dto = participantMapper.toParticipantDto(user);

                // Logic phân loại trạng thái cực kỳ quan trọng cho UI
                if (hostId.equals(userId)) {
                    dto.setStatus("HOST");
                } else if (raisedHandIds.contains(userId)) {
                    dto.setStatus("RAISING_HAND");
                } else {
                    dto.setStatus(ParticipantStatus.ACTIVE.name());
                }

                participants.add(dto);
            }
        }

        // 6. Sắp xếp lại danh sách: Ưu tiên Host lên đầu cùng, sau đó giữ nguyên thứ tự Join
        participants.sort((p1, p2) -> {
            if ("HOST".equals(p1.getStatus()) && !"HOST".equals(p2.getStatus())) return -1;
            if (!"HOST".equals(p1.getStatus()) && "HOST".equals(p2.getStatus())) return 1;
            return 0;
        });

        return participants;
    }
    @Override
    @Transactional
    public JoinMeetingResponse joinMeeting(String meetingCode, String meetingPassword) {
        String internalUserId = UserContext.getUserId();
        log.info("ServiceImpl: User (Internal) [{}] cố gắng join phòng [{}]", internalUserId, meetingCode);

        // 1. Tìm Meeting
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp: " + meetingCode));

        // 2. Tìm User để lấy thông tin
        UserEntity user = userRepository.findById(internalUserId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại: " + internalUserId));

        log.error("-------------------------------------------");
        log.error("DATABASE HOST ID: [{}] (Length: {})", meeting.getHostId(),
                meeting.getHostId() != null ? meeting.getHostId().length() : 0);
        log.error("CURRENT USER  ID: [{}] (Length: {})", internalUserId,
                internalUserId != null ? internalUserId.length() : 0);
        log.error("SO SANH BANG EQUALS: {}", meeting.getHostId().equals(internalUserId));
        log.error("-------------------------------------------");
        // 3. 🔥 ĐẶC QUYỀN CỦA HOST: Check Host trước
        boolean isHost = meeting.getHostId().equals(internalUserId);

        if (isHost) {
            log.info("✅ Host [{}] vào phòng [{}]", internalUserId, meetingCode);
            addActiveParticipant(meetingCode, internalUserId); // ⚠️ DÙNG ID NỘI BỘ VÀO REDIS
            return JoinMeetingResponse.builder()
                    .meetingCode(meetingCode)
                    .userId(internalUserId)
                    .status(ParticipantStatus.APPROVED.name())
                    .message("Welcome Host! You are now in the meeting")
                    .build();
        }

        // 4. KIỂM TRA MẬT KHẨU (Chỉ áp dụng cho Guest)
        if (meeting.getMeetingPassword() != null && !meeting.getMeetingPassword().isEmpty()) {
            if (meetingPassword == null || !meetingPassword.equals(meeting.getMeetingPassword())) {
                log.warn("🚨 Guest [{}] nhập password sai cho phòng [{}]", internalUserId, meetingCode);
                throw new SecurityException("Password không chính xác!");
            }
        }

        // 5. LOGIC PHÒNG CHỜ
        boolean isWaitingRoomEnabled = meeting.getIsWaitingRoomEnabled() != null && meeting.getIsWaitingRoomEnabled();

        if (!isWaitingRoomEnabled) {
            log.info("Guest [{}] vào phòng [{}]", internalUserId, meetingCode);
            addActiveParticipant(meetingCode, internalUserId);
            return JoinMeetingResponse.builder()
                    .meetingCode(meetingCode)
                    .userId(internalUserId)
                    .status(ParticipantStatus.APPROVED.name())
                    .message("You have joined the meeting!")
                    .build();
        }

        log.info(" [{}] đang chờ duyệt vào phòng [{}]", internalUserId, meetingCode);
        addWaitingParticipant(meetingCode, internalUserId);
        notifyHostAboutKnock(meetingCode, user);

        return JoinMeetingResponse.builder()
                .meetingCode(meetingCode)
                .userId(internalUserId)
                .status(ParticipantStatus.WAITING.name())
                .message("Please wait for the host to approve.")
                .build();
    }

    private void addActiveParticipant(String meetingCode, String userId) {
        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(activeKey, userId, score);

        redisTemplate.expire(activeKey, ACTIVE_PARTICIPANTS_TTL_HOURS, TimeUnit.HOURS);

        presenceService.addOnlineUser(meetingCode, userId);
        log.debug(" Added active participant [{}] to room [{}] with TTL={} hours",
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

    @Override
    public void toggleRaiseHand(String meetingCode, boolean isRaising) {
        String internalUserId = UserContext.getUserId();
        UserEntity user = userRepository.findById(internalUserId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        String key = RAISED_HANDS_PREFIX + meetingCode;

        Map<String, Object> payload = new HashMap<>();

        if (isRaising) {
            // Tối ưu TTL: Chỉ gọi expire khi key mới tinh
            Boolean exists = redisTemplate.hasKey(key);
            redisTemplate.opsForZSet().add(key, internalUserId, System.currentTimeMillis());
            if (Boolean.FALSE.equals(exists)) {
                redisTemplate.expire(key, 12, TimeUnit.HOURS);
            }

            // Gửi Delta Update kèm theo DTO đầy đủ
            ParticipantDto dto = participantMapper.toParticipantDto(user);
            dto.setStatus("RAISING_HAND");

            payload.put("action", "RAISE");
            payload.put("data", dto);
        } else {
            // Xóa khỏi Redis
            redisTemplate.opsForZSet().remove(key, internalUserId);

            // Gửi Delta Update gọn nhẹ (Chỉ ID)
            payload.put("action", "LOWER");
            payload.put("userId", internalUserId);
        }

        messagingTemplate.convertAndSend("/topic/meeting." + meetingCode + ".raised-hands", payload);
    }

    @Override
    @Transactional(readOnly = true)
    public RaisedHandResponse getRaisedHands(String meetingCode) {
        String key = RAISED_HANDS_PREFIX + meetingCode;

        Set<Object> rawIds = redisTemplate.opsForZSet().range(key, 0, -1);
        if (rawIds == null || rawIds.isEmpty()) {
            return new RaisedHandResponse(meetingCode, 0, Collections.emptyList());
        }

        List<String> orderedIds = new ArrayList<>(rawIds.size());
        for (Object rawId : rawIds) {
            orderedIds.add(rawId.toString());
        }

        List<UserEntity> users = userRepository.findAllById(orderedIds);

        // 4. Khởi tạo HashMap chống Rehash (Collision) bằng cách chia hệ số tải 0.75
        int mapCapacity = (int) (users.size() / 0.75f) + 1;
        Map<String, UserEntity> userMap = new HashMap<>(mapCapacity);
        for (UserEntity user : users) {
            userMap.put(user.getId(), user);
        }

        List<ParticipantDto> participantDtos = new ArrayList<>(orderedIds.size());
        for (String id : orderedIds) {
            UserEntity user = userMap.get(id);
            if (user != null) {
                ParticipantDto dto = participantMapper.toParticipantDto(user);
                dto.setStatus("RAISING_HAND");
                participantDtos.add(dto);
            }
        }

        return new RaisedHandResponse(meetingCode, participantDtos.size(), participantDtos);
    }

    private void broadcastDelta(String meetingCode, String action, String userId) {
        // Payload gọn nhẹ: { "action": "RAISE", "userId": "uuid-123" }
        messagingTemplate.convertAndSend(
                "/topic/meeting." + meetingCode + ".raised-hands",
                Map.of("action", action, "userId", userId));
    }

    /**
     * Gửi WebSocket notification đến Host về knock request
     */
    private void notifyHostAboutKnock(String meetingCode, UserEntity user) {
        try {
            String userId = user.getId();
            String pendingKnockKey = PENDING_KNOCK_PREFIX + meetingCode + ":" + userId;

            // Kiểm tra xem user này đã knock gần đây chưa
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
                            "message", hostNotificationMessage));
            log.info(" Sent knock notification for user [{}] to room [{}]", userId, meetingCode);
        } catch (Exception e) {
            log.error(" Error sending knock notification for user [{}]", user.getId(), e);
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
