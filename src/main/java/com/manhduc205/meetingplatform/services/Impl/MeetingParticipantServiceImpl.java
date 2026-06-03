package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.enums.ParticipantRole;
import com.manhduc205.meetingplatform.models.MeetingParticipantEntity;
import com.manhduc205.meetingplatform.models.dtos.mappers.ParticipantMapper;
import com.manhduc205.meetingplatform.models.dtos.response.*;
import com.manhduc205.meetingplatform.enums.ParticipantStatus;
import com.manhduc205.meetingplatform.enums.WaitingRoomAction;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.models.UserEntity;
import com.manhduc205.meetingplatform.repositories.MeetingParticipantRepository;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.MeetingParticipantJoinRecorder;
import com.manhduc205.meetingplatform.services.MeetingParticipantService;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingParticipantServiceImpl implements MeetingParticipantService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingParticipantJoinRecorder joinRecorder;
    private final ParticipantMapper participantMapper;
    private final MeetingPresenceService presenceService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String ACTIVE_PARTICIPANTS_PREFIX = "active:participants:";
    private static final String WAITING_PARTICIPANTS_PREFIX = "waiting:participants:";
    private static final String PENDING_KNOCK_PREFIX = "pending:knock:";
    private static final String RAISED_HANDS_PREFIX = "meeting:raised_hands:";
    private static final String WAITING_ROOM_TOPIC_SUFFIX = ".waiting-room";

    private static final int DISPLAY_LIMIT = 10;
    private static final long ACTIVE_PARTICIPANTS_TTL_HOURS = 12;
    private static final long WAITING_PARTICIPANTS_TTL_MINUTES = 30;
    private static final long PENDING_KNOCK_TTL_SECONDS = 30;


    @Override
    @Transactional(readOnly = true)
    public ActiveParticipantsResponse getActiveParticipants(String meetingCode) {
        String internalUserId = UserContext.getUserId();
        meetingRepository.findByMeetingCode(meetingCode).orElseThrow();

        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        // Lấy nhiều hơn DISPLAY_LIMIT một chút để phòng trường hợp có chính mình trong đó
        Set<Object> activeUserIds = redisTemplate.opsForZSet().range(activeKey, 0, DISPLAY_LIMIT);

        if (activeUserIds == null || activeUserIds.isEmpty()) {
            return ActiveParticipantsResponse.builder()
                    .totalCount(0).participants(Collections.emptyList()).build();
        }

        List<String> userIdList = activeUserIds.stream().map(Object::toString).toList();
        List<UserEntity> users = userRepository.findAllById(userIdList);
        Map<String, UserEntity> userMap = buildUserMap(users);

        List<ParticipantDto> otherParticipants = new ArrayList<>();
        ParticipantDto currentUserDto = null;

        for (String id : userIdList) {
            UserEntity u = userMap.get(id);
            if (u != null) {
                ParticipantDto dto = participantMapper.toParticipantDto(u);
                dto.setStatus(ParticipantStatus.ACTIVE.name());

                if (id.equals(internalUserId)) {
                    currentUserDto = dto;
                } else {
                    otherParticipants.add(dto);
                }
            }
        }

        // Nếu danh sách "người khác" quá dài thì cắt bớt cho đúng DISPLAY_LIMIT
        if (otherParticipants.size() > DISPLAY_LIMIT - 1) {
            otherParticipants = otherParticipants.subList(0, DISPLAY_LIMIT - 1);
        }

        Long totalCount = redisTemplate.opsForZSet().size(activeKey);

        return ActiveParticipantsResponse.builder()
                .totalCount(totalCount != null ? totalCount.intValue() : 0)
                .participants(otherParticipants)
                .currentUser(currentUserDto) // Trả về riêng ở đây
                .displayText(buildDisplayText(otherParticipants, totalCount != null ? totalCount.intValue() : 0))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantDto> getAllParticipants(String meetingCode) {
        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        Set<Object> activeUserIds = redisTemplate.opsForZSet().range(activeKey, 0, -1);
        if (activeUserIds == null || activeUserIds.isEmpty()) return Collections.emptyList();

        List<String> userIdList = activeUserIds.stream().map(Object::toString).toList();
        return userRepository.findAllById(userIdList).stream()
                .map(participantMapper::toParticipantDto)
                .peek(dto -> dto.setStatus(ParticipantStatus.ACTIVE.name()))
                .collect(Collectors.toList());
    }

    /**
     * 🟢 THUẬT TOÁN 3: LẤY DANH SÁCH (STREAM API)
     * API chuyên dụng dành cho Host vào xem thống kê.
     */
    @Override
    public List<ParticipantAttendanceResponse> getMeetingAttendanceHistory(String meetingCode) {
        String currentUserId = UserContext.getUserId();

        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại"));

        // Bảo mật phân quyền cấp Object: Chỉ Host mới được xem
        if (!meeting.getHostId().equals(currentUserId)) {
            throw new SecurityException("Truy cập bị từ chối. Chỉ chủ phòng mới có quyền xem Sổ điểm danh.");
        }

        List<MeetingParticipantEntity> participants = participantRepository.findAllByMeetingIdOrderByJoinedOnceAtAsc(meeting.getId());

        // Sử dụng Stream API để ánh xạ DTO
        return participants.stream().map(p -> {
            // Mock dữ liệu Profile (Sau này em gọi hàm UserService/Keycloak để đắp tên thật vào đây)
            String mockedName = "ID: " + p.getUserId().substring(0, Math.min(8, p.getUserId().length()));
            String mockedAvatar = "https://ui-avatars.com/api/?name=User&background=random";

            return ParticipantAttendanceResponse.builder()
                    .userId(p.getUserId())
                    .fullName(mockedName)
                    .avatar(mockedAvatar)
                    .role(p.getRole())
                    .joinedAt(p.getJoinedOnceAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantDto> getSidebarParticipants(String meetingCode) {
        String internalUserId = UserContext.getUserId(); // Lấy ID người đang gọi API
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode).orElseThrow();
        String hostId = meeting.getHostId();

        // 1. Lấy toàn bộ ID từ Redis
        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        Set<Object> activeUserIds = redisTemplate.opsForZSet().range(activeKey, 0, -1);
        if (activeUserIds == null || activeUserIds.isEmpty()) return Collections.emptyList();

        // 2. Lấy danh sách giơ tay để gán status
        String raisedHandKey = RAISED_HANDS_PREFIX + meetingCode;
        Set<Object> raisedHands = redisTemplate.opsForZSet().range(raisedHandKey, 0, -1);
        Set<String> raisedHandIds = (raisedHands != null)
                ? raisedHands.stream().map(Object::toString).collect(Collectors.toSet()) : Collections.emptySet();

        List<String> userIdList = activeUserIds.stream().map(Object::toString).toList();
        List<UserEntity> users = userRepository.findAllById(userIdList);
        Map<String, UserEntity> userMap = buildUserMap(users);

        List<ParticipantDto> participants = new ArrayList<>();

        for (String userId : userIdList) {
            UserEntity user = userMap.get(userId);
            if (user != null) {
                ParticipantDto dto = participantMapper.toParticipantDto(user);

                // XÁC ĐỊNH STATUS THEO THỨ TỰ ƯU TIÊN
                if (hostId.equals(userId)) {
                    dto.setStatus("HOST");
                } else if (raisedHandIds.contains(userId)) {
                    dto.setStatus("RAISING_HAND");
                } else {
                    dto.setStatus(ParticipantStatus.ACTIVE.name());
                }

                // ĐÁNH DẤU NẾU LÀ CHÍNH MÌNH
                if (userId.equals(internalUserId)) {
                    dto.setIsMe(true); // Em nên thêm field boolean isMe vào ParticipantDto
                }

                participants.add(dto);
            }
        }

        // 3. SẮP XẾP ĐA TẦNG (MULTIPLEX SORTING)
        participants.sort((p1, p2) -> {
            // Tầng 1: Host luôn là số 1
            if ("HOST".equals(p1.getStatus())) return -1;
            if ("HOST".equals(p2.getStatus())) return 1;

            // Tầng 2: "Tôi" luôn là số 2 (sau Host)
            if (p1.getIsMe() != null && p1.getIsMe()) return -1;
            if (p2.getIsMe() != null && p2.getIsMe()) return 1;

            // Tầng 3: Người giơ tay ưu tiên lên trước người Active
            if ("RAISING_HAND".equals(p1.getStatus()) && "ACTIVE".equals(p2.getStatus())) return -1;
            if ("ACTIVE".equals(p1.getStatus()) && "RAISING_HAND".equals(p2.getStatus())) return 1;

            return 0;
        });

        return participants;
    }

    // ==============================================================================
    // 2. NGHIỆP VỤ JOIN & PHÒNG CHỜ (WAITING ROOM)
    // ==============================================================================

    @Override
    @Transactional
    public JoinMeetingResponse joinMeeting(String meetingCode, String meetingPassword) {
        String internalUserId = UserContext.getUserId();
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode).orElseThrow();
        UserEntity user = userRepository.findById(internalUserId).orElseThrow();

        if (meeting.getHostId().equals(internalUserId)) {
            addActiveParticipant(meetingCode, internalUserId);
            joinRecorder.recordParticipantJoinAsync(meeting.getId(), internalUserId, ParticipantRole.HOST);
            return JoinMeetingResponse.builder().meetingCode(meetingCode).userId(internalUserId)
                    .status(ParticipantStatus.APPROVED.name()).build();
        }

        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        String waitingKey = WAITING_PARTICIPANTS_PREFIX + meetingCode;

        // Nếu đã lọt vào danh sách ACTIVE (phòng không có phòng chờ) -> Trả về luôn
        if (redisTemplate.opsForZSet().score(activeKey, internalUserId) != null) {
            joinRecorder.recordParticipantJoinAsync(meeting.getId(), internalUserId, ParticipantRole.PARTICIPANT);
            return JoinMeetingResponse.builder().meetingCode(meetingCode).userId(internalUserId)
                    .status(ParticipantStatus.APPROVED.name()).message("You are already in the meeting!").build();
        }

        // Nếu đang nằm trong phòng chờ WAITING -> Trả về luôn
        if (redisTemplate.opsForZSet().score(waitingKey, internalUserId) != null) {
            log.info("Bypass check pass: User [{}] đã có sẵn trong WAITING phòng [{}]", internalUserId, meetingCode);
            return JoinMeetingResponse.builder().meetingCode(meetingCode).userId(internalUserId)
                    .status(ParticipantStatus.WAITING.name()).message("You are already in the waiting room.").build();
        }
        // =====================================================================

        // 2. Kiểm tra mật khẩu (Chỉ dành cho người mới gọi API lần đầu)
        if (meeting.getMeetingPassword() != null && !meeting.getMeetingPassword().isEmpty()) {
            if (meetingPassword == null || !meeting.getMeetingPassword().equals(meetingPassword)) {
                throw new SecurityException("Password không chính xác!");
            }
        }

        // 3. Logic phòng chờ
        if (meeting.getIsWaitingRoomEnabled() != null && !meeting.getIsWaitingRoomEnabled()) {
            addActiveParticipant(meetingCode, internalUserId);
            joinRecorder.recordParticipantJoinAsync(meeting.getId(), internalUserId, ParticipantRole.PARTICIPANT);
            return JoinMeetingResponse.builder().meetingCode(meetingCode).userId(internalUserId)
                    .status(ParticipantStatus.APPROVED.name()).message("Joined successfully!").build();
        }

        addWaitingParticipant(meetingCode, internalUserId);
        notifyHostAboutKnock(meetingCode, user);

        return JoinMeetingResponse.builder().meetingCode(meetingCode).userId(internalUserId)
                .status(ParticipantStatus.WAITING.name()).message("Waiting for host approval.").build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantDto> getWaitingParticipants(String meetingCode) {
        String waitingKey = WAITING_PARTICIPANTS_PREFIX + meetingCode;
        Set<Object> waitingIds = redisTemplate.opsForZSet().range(waitingKey, 0, -1);
        if (waitingIds == null || waitingIds.isEmpty()) return Collections.emptyList();

        List<String> userIdList = waitingIds.stream().map(Object::toString).toList();
        List<UserEntity> users = userRepository.findAllById(userIdList);
        Map<String, UserEntity> userMap = buildUserMap(users);

        List<ParticipantDto> participants = new ArrayList<>();
        for (String userId : userIdList) {
            UserEntity user = userMap.get(userId);
            if (user != null) {
                ParticipantDto dto = participantMapper.toParticipantDto(user);
                dto.setStatus(ParticipantStatus.WAITING.name());
                participants.add(dto);
            }
        }
        return participants;
    }

    @Override
    public void processWaitingParticipants(String meetingCode, List<String> userIds, WaitingRoomAction action) {
        validateHostPrivilege(meetingCode);

        String waitingKey = WAITING_PARTICIPANTS_PREFIX + meetingCode;
        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;

        Set<String> idsToProcess = new HashSet<>();
        if (userIds == null || userIds.isEmpty()) {
            Set<Object> waitingIds = redisTemplate.opsForZSet().range(waitingKey, 0, -1);
            if (waitingIds != null) waitingIds.forEach(id -> idsToProcess.add(id.toString()));
        } else {
            idsToProcess.addAll(userIds);
        }

        if (idsToProcess.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        for (String targetId : idsToProcess) {
            redisTemplate.opsForZSet().remove(waitingKey, targetId);
            if (action == WaitingRoomAction.APPROVE) {
                redisTemplate.opsForZSet().add(activeKey, targetId, currentTime++);
                presenceService.addOnlineUser(meetingCode, targetId);
                broadcastWaitingRoomStatus(meetingCode, targetId, "APPROVED");
            } else {
                broadcastWaitingRoomStatus(meetingCode, targetId, "REJECTED");
            }
        }
    }

    @Override
    public void toggleRaiseHand(String meetingCode, boolean isRaising) {
        String internalUserId = UserContext.getUserId();
        UserEntity user = userRepository.findById(internalUserId).orElseThrow();
        String key = RAISED_HANDS_PREFIX + meetingCode;
        Map<String, Object> payload = new HashMap<>();

        if (isRaising) {
            Boolean exists = redisTemplate.hasKey(key);
            redisTemplate.opsForZSet().add(key, internalUserId, System.currentTimeMillis());
            if (Boolean.FALSE.equals(exists)) redisTemplate.expire(key, 12, TimeUnit.HOURS);

            ParticipantDto dto = participantMapper.toParticipantDto(user);
            dto.setStatus("RAISING_HAND");
            payload.put("action", "RAISE");
            payload.put("data", dto);
        } else {
            redisTemplate.opsForZSet().remove(key, internalUserId);
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
        if (rawIds == null || rawIds.isEmpty()) return new RaisedHandResponse(meetingCode, 0, Collections.emptyList());

        List<String> orderedIds = rawIds.stream().map(Object::toString).toList();
        List<UserEntity> users = userRepository.findAllById(orderedIds);
        Map<String, UserEntity> userMap = buildUserMap(users);

        List<ParticipantDto> participantDtos = new ArrayList<>();
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

    // ==============================================================================
    // PRIVATE HELPERS
    // ==============================================================================

    private Map<String, UserEntity> buildUserMap(List<UserEntity> users) {
        int capacity = (int) (users.size() / 0.75f) + 1;
        Map<String, UserEntity> map = new HashMap<>(capacity);
        for (UserEntity user : users) map.put(user.getId(), user);
        return map;
    }

    private void addActiveParticipant(String meetingCode, String userId) {
        String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
        redisTemplate.opsForZSet().add(activeKey, userId, System.currentTimeMillis());
        redisTemplate.expire(activeKey, ACTIVE_PARTICIPANTS_TTL_HOURS, TimeUnit.HOURS);
        presenceService.addOnlineUser(meetingCode, userId);
    }

    private void addWaitingParticipant(String meetingCode, String userId) {
        String waitingKey = WAITING_PARTICIPANTS_PREFIX + meetingCode;
        redisTemplate.opsForZSet().add(waitingKey, userId, System.currentTimeMillis());
        redisTemplate.expire(waitingKey, WAITING_PARTICIPANTS_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private void validateHostPrivilege(String meetingCode) {
        String internalUserId = UserContext.getUserId();
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode).orElseThrow();
        if (!meeting.getHostId().equals(internalUserId)) {
            throw new SecurityException("Chỉ chủ phòng mới có quyền duyệt người tham gia!");
        }
    }

    private void broadcastWaitingRoomStatus(String meetingCode, String targetUserId, String action) {
        messagingTemplate.convertAndSend(
                "/topic/meeting." + meetingCode + WAITING_ROOM_TOPIC_SUFFIX,
                Map.of("action", action, "userId", targetUserId)
        );
    }

    private void notifyHostAboutKnock(String meetingCode, UserEntity user) {
        try {
            String pendingKnockKey = PENDING_KNOCK_PREFIX + meetingCode + ":" + user.getId();
            if (Boolean.TRUE.equals(redisTemplate.hasKey(pendingKnockKey))) return;

            redisTemplate.opsForValue().set(pendingKnockKey, "KNOCKING", PENDING_KNOCK_TTL_SECONDS, TimeUnit.SECONDS);
            String userName = user.getFullName() != null ? user.getFullName() : user.getEmail();

            messagingTemplate.convertAndSend(
                    "/topic/meeting." + meetingCode + ".host-notifications",
                    Map.of("type", "KNOCK_REQUEST", "userId", user.getId(), "userName", userName,
                            "avatarUrl", user.getAvatarUrl(), "message", userName + " đang xin vào phòng"));
        } catch (Exception e) {
            log.error("Lỗi gửi notification knock: {}", e.getMessage());
        }
    }

    private String buildDisplayText(List<ParticipantDto> participants, int totalCount) {
        if (participants.isEmpty()) return "No one is here yet";
        StringBuilder nameList = new StringBuilder();
        int displayCount = Math.min(2, participants.size());
        for (int i = 0; i < displayCount; i++) {
            if (i > 0) nameList.append(", ");
            nameList.append(participants.get(i).getFirstName() != null ? participants.get(i).getFirstName() : "Someone");
        }
        int othersCount = totalCount - displayCount;
        if (othersCount > 0) return String.format("%s, and %d others are already here", nameList, othersCount);
        return nameList.append(displayCount > 1 ? " are" : " is").append(" already here").toString();
    }
}
