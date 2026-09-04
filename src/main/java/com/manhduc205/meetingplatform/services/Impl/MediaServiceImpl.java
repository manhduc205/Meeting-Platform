package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.models.dtos.response.MediaJoinResponse;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.enums.MeetingStatus;
import com.manhduc205.meetingplatform.exceptions.MeetingJoinDeniedException;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.MediaService;
import com.manhduc205.meetingplatform.services.MediaTokenService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaTokenService mediaTokenService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;

    @Value("${app.livekit.host}")
    private String liveKitHost;
    @Value("${app.livekit.public-url}")
    private String liveKitPublicUrl;
    @Value("${app.webrtc.stun-url}")
    private String stunUrl;
    @Value("${app.webrtc.turn-url}")
    private String turnUrl;
    @Value("${app.webrtc.turn-username}")
    private String turnUsername;
    @Value("${app.webrtc.turn-password}")
    private String turnPassword;

    private static final String ADMITTED_PARTICIPANTS_PREFIX = "admitted:participants:";
    private static final String KICKED_PARTICIPANT_PREFIX = "meeting:kicked:";

    @Override
    public MediaJoinResponse prepareMediaConnection(String meetingCode) {
        String internalUserId = UserContext.getUserId();

        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp"));
        var user = userRepository.findById(internalUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cuộc họp chưa được Host bắt đầu hoặc đã kết thúc.");
        }

        // 1. Kiểm tra phòng bị khóa — từ chối tất cả Guest kết nối mới
        if (Boolean.TRUE.equals(meeting.getIsLocked())) {
            log.warn("Guest [{}] cố join phòng [{}] đang bị khóa!", internalUserId, meetingCode);
            throw new SecurityException("Phòng họp đã bị khóa, không thể tham gia!");
        }

        boolean isHost = meeting.getHostId().equals(internalUserId);

        if (!isHost) {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                    KICKED_PARTICIPANT_PREFIX + meetingCode + ":" + internalUserId))) {
                throw new MeetingJoinDeniedException(
                        "PARTICIPANT_KICKED", "Bạn đã bị chủ phòng mời ra và không thể tham gia lại cuộc họp này");
            }

            boolean admitted = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(
                    ADMITTED_PARTICIPANTS_PREFIX + meetingCode, internalUserId));
            if (!admitted) {
                log.warn("Guest [{}] cố lấy token media phòng [{}] khi chưa được duyệt",
                        internalUserId, meetingCode);
                throw new SecurityException("Bạn chưa được Host duyệt vào phòng!");
            }
        } else {
            log.info("Host [{}] đang khởi tạo luồng Media cho phòng [{}]", internalUserId, meetingCode);
        }

        // 4. Đóng gói cấu hình ICE (STUN/TURN)
        MediaJoinResponse.IceServerConfig iceConfig = MediaJoinResponse.IceServerConfig.builder()
                .stunUrl(stunUrl)
                .turnUrl(turnUrl)
                .username(turnUsername)
                .credential(turnPassword)
                .build();

        // 5. Sinh Token LiveKit
        String displayName = user.getFullName() == null || user.getFullName().isBlank()
                ? user.getEmail()
                : user.getFullName();
        String liveKitToken = mediaTokenService.generateLiveKitToken(meetingCode, internalUserId, displayName);

        return MediaJoinResponse.builder()
                .mode("SFU")
                .token(liveKitToken)
                .serverUrl(liveKitPublicUrl)
                .iceServers(iceConfig)
                .build();
    }
}
