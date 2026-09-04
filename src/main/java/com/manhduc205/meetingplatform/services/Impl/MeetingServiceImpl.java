package com.manhduc205.meetingplatform.services.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.meetingplatform.enums.*;
import com.manhduc205.meetingplatform.exceptions.ResourceNotFoundException;
import com.manhduc205.meetingplatform.models.*;
import com.manhduc205.meetingplatform.models.dtos.request.*;
import com.manhduc205.meetingplatform.models.dtos.response.CalendarMeetingResponse;
import com.manhduc205.meetingplatform.models.dtos.response.InvitationResponse;
import com.manhduc205.meetingplatform.models.dtos.response.MeetingResponse;
import com.manhduc205.meetingplatform.repositories.MeetingInvitationRepository;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.OutboxEventRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.MeetingService;
import com.manhduc205.meetingplatform.services.RecordingService;
import com.manhduc205.meetingplatform.utils.UserContext;
import io.livekit.server.RoomServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingServiceImpl implements MeetingService {
    private static final int MAX_INVITEES = 50;

    private final MeetingRepository meetingRepository;
    private final MeetingInvitationRepository invitationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UserRepository userRepository;
    private final RecordingService recordingService;
    private final RoomServiceClient roomServiceClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.notifications.public-base-url}")
    private String publicBaseUrl;

    @Override
    @Transactional
    public MeetingResponse createMeeting(MeetingCreateRequest request) {
        validateSchedule(request.getPlannedStartTime(), request.getPlannedEndTime());
        String hostId = UserContext.getUserId();
        UserEntity host = getUser(hostId);
        MeetingEntity meeting = MeetingEntity.builder()
                .meetingCode(generateMeetingCode()).hostId(hostId).title(request.getTitle().trim())
                .description(request.getDescription()).meetingPassword(encodePassword(request.getMeetingPassword()))
                .isWaitingRoomEnabled(request.getIsWaitingRoomEnabled() == null || request.getIsWaitingRoomEnabled())
                .plannedStartTime(request.getPlannedStartTime()).plannedEndTime(request.getPlannedEndTime())
                .status(MeetingStatus.SCHEDULED).build();
        meeting = meetingRepository.save(meeting);
        createInvitations(meeting, host, request.getInviteeEmails(), request.getMeetingPassword());
        return toMeetingResponse(meeting);
    }

    @Override
    @Transactional
    public MeetingResponse createInstantMeeting(InstantMeetingCreateRequest request) {
        String hostId = UserContext.getUserId();
        UserEntity host = getUser(hostId);
        Instant now = Instant.now();
        MeetingEntity meeting = MeetingEntity.builder()
                .meetingCode(generateMeetingCode()).hostId(hostId).title(request.getTitle().trim())
                .description(request.getDescription()).meetingPassword(encodePassword(request.getMeetingPassword()))
                .isWaitingRoomEnabled(request.getIsWaitingRoomEnabled() == null || request.getIsWaitingRoomEnabled())
                .plannedStartTime(now).plannedEndTime(now.plus(2, ChronoUnit.HOURS))
                .startedAt(now).status(MeetingStatus.IN_PROGRESS).build();
        meeting = meetingRepository.save(meeting);
        createInvitations(meeting, host, request.getInviteeEmails(), request.getMeetingPassword());
        publishMeetingEvent(meeting.getMeetingCode(), "MEETING_STARTED");
        return toMeetingResponse(meeting);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(String meetingCode) {
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));
        return toMeetingResponse(meeting);
    }

    @Override
    @Transactional
    public MeetingResponse updateMeeting(String meetingCode, MeetingUpdateRequest request) {
        validateSchedule(request.getPlannedStartTime(), request.getPlannedEndTime());
        MeetingEntity meeting = getOwnedMeeting(meetingCode);
        requireStatus(meeting, MeetingStatus.SCHEDULED, "Chỉ có thể sửa lịch họp chưa bắt đầu.");
        meeting.setTitle(request.getTitle().trim());
        meeting.setDescription(request.getDescription());
        if (request.getMeetingPassword() != null) meeting.setMeetingPassword(encodePassword(request.getMeetingPassword()));
        meeting.setIsWaitingRoomEnabled(request.getIsWaitingRoomEnabled() == null || request.getIsWaitingRoomEnabled());
        meeting.setPlannedStartTime(request.getPlannedStartTime());
        meeting.setPlannedEndTime(request.getPlannedEndTime());
        return toMeetingResponse(meetingRepository.save(meeting));
    }

    @Override
    @Transactional
    public MeetingResponse startMeeting(String meetingCode) {
        MeetingEntity meeting = getOwnedMeeting(meetingCode);
        requireStatus(meeting, MeetingStatus.SCHEDULED, "Cuộc họp không ở trạng thái có thể bắt đầu.");
        meeting.setStatus(MeetingStatus.IN_PROGRESS);
        meeting.setStartedAt(Instant.now());
        MeetingEntity saved = meetingRepository.save(meeting);
        publishMeetingEvent(meetingCode, "MEETING_STARTED");
        return toMeetingResponse(saved);
    }

    @Override
    @Transactional
    public MeetingResponse endMeeting(String meetingCode) {
        MeetingEntity meeting = getOwnedMeeting(meetingCode);
        requireStatus(meeting, MeetingStatus.IN_PROGRESS, "Chỉ có thể kết thúc cuộc họp đang diễn ra.");
        recordingService.stopActiveRecordings(meetingCode);
        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setEndedAt(Instant.now());
        MeetingEntity saved = meetingRepository.save(meeting);
        closeLiveKitRoom(meetingCode);
        publishMeetingEvent(meetingCode, "MEETING_ENDED");
        return toMeetingResponse(saved);
    }

    @Override
    @Transactional
    public void cancelMeeting(String meetingCode) {
        MeetingEntity meeting = getOwnedMeeting(meetingCode);
        requireStatus(meeting, MeetingStatus.SCHEDULED, "Chỉ có thể hủy lịch họp chưa bắt đầu.");
        meeting.setStatus(MeetingStatus.CANCELLED);
        meetingRepository.save(meeting);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingResponse> getMyMeetings() {
        return meetingRepository.findAllByHostIdOrderByPlannedStartTimeAsc(UserContext.getUserId()).stream()
                .map(this::toMeetingResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarMeetingResponse> getCalendar(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) throw new IllegalArgumentException("Khoảng thời gian Calendar không hợp lệ.");
        String userId = UserContext.getUserId();
        UserEntity user = getUser(userId);
        Map<String, InvitationStatus> invitationStatuses = invitationRepository
                .findAllByInviteeEmailAndStatusIn(user.getEmail().toLowerCase(Locale.ROOT), List.of(InvitationStatus.PENDING, InvitationStatus.ACCEPTED))
                .stream().collect(Collectors.toMap(MeetingInvitationEntity::getMeetingId, MeetingInvitationEntity::getStatus, (left, right) -> left));
        Map<String, MeetingEntity> meetings = new LinkedHashMap<>();
        meetingRepository.findAllByHostIdAndPlannedStartTimeLessThanAndPlannedEndTimeGreaterThanOrderByPlannedStartTimeAsc(userId, to, from)
                .forEach(meeting -> meetings.put(meeting.getId(), meeting));
        meetingRepository.findAllById(invitationStatuses.keySet()).forEach(meeting -> {
            if (overlaps(meeting, from, to)) meetings.putIfAbsent(meeting.getId(), meeting);
        });
        return meetings.values().stream().filter(meeting -> meeting.getStatus() != MeetingStatus.CANCELLED && meeting.getStatus() != MeetingStatus.ENDED)
                .sorted(Comparator.comparing(MeetingEntity::getPlannedStartTime))
                .map(meeting -> toCalendarResponse(meeting, userId, invitationStatuses.get(meeting.getId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarMeetingResponse> getUpcoming(int limit) {
        int resolvedLimit = Math.min(Math.max(limit, 1), 50);
        Instant now = Instant.now();
        return getCalendar(now.minus(1, ChronoUnit.DAYS), now.plus(90, ChronoUnit.DAYS)).stream()
                .filter(meeting -> meeting.getStatus() == MeetingStatus.IN_PROGRESS || meeting.getPlannedStartTime().isAfter(now))
                .limit(resolvedLimit).toList();
    }

    @Override
    @Transactional
    public List<InvitationResponse> addInvitations(String meetingCode, InvitationCreateRequest request) {
        MeetingEntity meeting = getOwnedMeeting(meetingCode);
        if (meeting.getStatus() != MeetingStatus.SCHEDULED && meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Chỉ có thể mời khách vào cuộc họp chưa kết thúc.");
        }
        return createInvitations(meeting, getUser(meeting.getHostId()), request.getInviteeEmails(), null).stream()
                .map(this::toInvitationResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> getInvitations(String meetingCode) {
        MeetingEntity meeting = getOwnedMeeting(meetingCode);
        return invitationRepository.findAllByMeetingIdOrderByCreatedAtAsc(meeting.getId()).stream()
                .map(this::toInvitationResponse).toList();
    }

    @Override
    @Transactional
    public InvitationResponse respondToInvitation(String invitationId, InvitationResponseRequest request) {
        if (request.getStatus() == InvitationStatus.PENDING) throw new IllegalArgumentException("Chỉ có thể đồng ý hoặc từ chối lời mời.");
        MeetingInvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lời mời."));
        UserEntity user = getUser(UserContext.getUserId());
        if (!invitation.getInviteeEmail().equalsIgnoreCase(user.getEmail())) throw new SecurityException("Bạn không có quyền phản hồi lời mời này.");
        invitation.setStatus(request.getStatus());
        invitation.setRespondedAt(Instant.now());
        invitation.setInviteeUserId(user.getId());
        return toInvitationResponse(invitationRepository.save(invitation));
    }

    private List<MeetingInvitationEntity> createInvitations(MeetingEntity meeting, UserEntity host, List<String> rawEmails, String rawPassword) {
        Set<String> emails = normalizeEmails(rawEmails);
        emails.remove(host.getEmail().toLowerCase(Locale.ROOT));
        if (emails.size() > MAX_INVITEES) throw new IllegalArgumentException("Mỗi lần chỉ được mời tối đa " + MAX_INVITEES + " người.");
        List<MeetingInvitationEntity> created = new ArrayList<>();
        for (String email : emails) {
            if (invitationRepository.existsByMeetingIdAndInviteeEmail(meeting.getId(), email)) continue;
            String inviteeUserId = userRepository.findByEmailIgnoreCase(email).map(UserEntity::getId).orElse(null);
            MeetingInvitationEntity invitation = invitationRepository.save(MeetingInvitationEntity.builder()
                    .meetingId(meeting.getId()).inviteeEmail(email).inviteeUserId(inviteeUserId)
                    .status(InvitationStatus.PENDING).build());
            enqueueInvitationEmail(meeting, host, invitation, rawPassword);
            created.add(invitation);
        }
        return created;
    }

    private void enqueueInvitationEmail(MeetingEntity meeting, UserEntity host, MeetingInvitationEntity invitation, String rawPassword) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientEmail", invitation.getInviteeEmail());
        payload.put("meetingCode", meeting.getMeetingCode());
        payload.put("title", meeting.getTitle());
        payload.put("hostName", host.getFullName());
        payload.put("plannedStartTime", meeting.getPlannedStartTime());
        payload.put("plannedEndTime", meeting.getPlannedEndTime());
        payload.put("joinUrl", publicBaseUrl.replaceAll("/$", "") + "/waiting-room?meetingId=" + meeting.getMeetingCode());
        if (rawPassword != null && !rawPassword.isBlank()) payload.put("meetingPassword", rawPassword);
        try {
            outboxEventRepository.save(OutboxEventEntity.builder().eventType(OutboxEventType.SEND_INVITATION_EMAIL)
                    .aggregateId(invitation.getId()).payload(objectMapper.writeValueAsString(payload))
                    .status(OutboxEventStatus.PENDING).nextRetryAt(Instant.now()).build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tạo thông điệp gửi email.", exception);
        }
    }

    private void validateSchedule(Instant start, Instant end) {
        Instant now = Instant.now();
        if (start == null || end == null) throw new IllegalArgumentException("Phải có thời gian bắt đầu và kết thúc.");
        if (start.isBefore(now)) throw new IllegalArgumentException("Thời gian bắt đầu không được nằm trong quá khứ.");
        if (end.isBefore(start.plus(Duration.ofMinutes(15)))) throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu ít nhất 15 phút.");
    }

    private Set<String> normalizeEmails(List<String> rawEmails) {
        if (rawEmails == null) return new LinkedHashSet<>();
        return rawEmails.stream().filter(Objects::nonNull).map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String encodePassword(String rawPassword) {
        return rawPassword == null || rawPassword.isBlank() ? null : passwordEncoder.encode(rawPassword);
    }

    private MeetingEntity getOwnedMeeting(String meetingCode) {
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp."));
        if (!meeting.getHostId().equals(UserContext.getUserId())) throw new SecurityException("Chỉ chủ phòng mới có quyền thực hiện thao tác này.");
        return meeting;
    }

    private UserEntity getUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
    }

    private void requireStatus(MeetingEntity meeting, MeetingStatus expected, String message) {
        if (meeting.getStatus() != expected) throw new IllegalStateException(message);
    }

    private boolean overlaps(MeetingEntity meeting, Instant from, Instant to) {
        return meeting.getPlannedStartTime().isBefore(to) && meeting.getPlannedEndTime().isAfter(from);
    }

    private MeetingResponse toMeetingResponse(MeetingEntity meeting) {
        return MeetingResponse.builder().id(meeting.getId()).meetingCode(meeting.getMeetingCode()).title(meeting.getTitle())
                .description(meeting.getDescription()).hostId(meeting.getHostId()).status(meeting.getStatus().name())
                .plannedStartTime(meeting.getPlannedStartTime()).plannedEndTime(meeting.getPlannedEndTime())
                .startedAt(meeting.getStartedAt()).endedAt(meeting.getEndedAt())
                .waitingRoomEnabled(Boolean.TRUE.equals(meeting.getIsWaitingRoomEnabled()))
                .hasPassword(meeting.getMeetingPassword() != null && !meeting.getMeetingPassword().isBlank())
                .createdAt(meeting.getCreatedAt()).googleEventId(meeting.getGoogleEventId()).build();
    }

    private CalendarMeetingResponse toCalendarResponse(MeetingEntity meeting, String userId, InvitationStatus invitationStatus) {
        boolean isHost = meeting.getHostId().equals(userId);
        UserEntity host = userRepository.findById(meeting.getHostId()).orElse(null);
        return CalendarMeetingResponse.builder().id(meeting.getId()).meetingCode(meeting.getMeetingCode()).title(meeting.getTitle())
                .description(meeting.getDescription()).hostId(meeting.getHostId()).hostName(host == null ? null : host.getFullName())
                .hostAvatarUrl(host == null ? null : host.getAvatarUrl()).plannedStartTime(meeting.getPlannedStartTime())
                .plannedEndTime(meeting.getPlannedEndTime()).status(meeting.getStatus()).isHost(isHost)
                .role(isHost ? "HOST" : "GUEST").invitationStatus(invitationStatus)
                .canStart(isHost && meeting.getStatus() == MeetingStatus.SCHEDULED)
                .canJoin(meeting.getStatus() == MeetingStatus.IN_PROGRESS).build();
    }

    private InvitationResponse toInvitationResponse(MeetingInvitationEntity invitation) {
        return InvitationResponse.builder().id(invitation.getId()).inviteeEmail(invitation.getInviteeEmail())
                .status(invitation.getStatus()).respondedAt(invitation.getRespondedAt()).createdAt(invitation.getCreatedAt()).build();
    }

    private String generateMeetingCode() {
        String code;
        do { code = String.valueOf(1000000000L + new java.security.SecureRandom().nextLong(9000000000L)); }
        while (meetingRepository.existsByMeetingCode(code));
        return code;
    }

    private void publishMeetingEvent(String meetingCode, String type) {
        messagingTemplate.convertAndSend("/topic/meeting." + meetingCode,
                Map.of("category", "ACTION", "type", type, "meetingCode", meetingCode, "timestamp", Instant.now().toString()));
    }

    private void closeLiveKitRoom(String meetingCode) {
        try {
            retrofit2.Response<Void> response = roomServiceClient.deleteRoom(meetingCode).execute();
            if (!response.isSuccessful() && response.code() != 404) log.warn("Không thể đóng LiveKit room {}: HTTP {}", meetingCode, response.code());
        } catch (IOException exception) {
            log.error("Không thể kết nối LiveKit để đóng room {}", meetingCode, exception);
        }
    }
}
