package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.response.MediaJoinResponse;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
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

    // 🔥 Inject thêm Repository để lấy thông tin Host
    private final MeetingRepository meetingRepository;

    @Value("${app.livekit.host}")
    private String liveKitHost;
    @Value("${app.webrtc.stun-url}")
    private String stunUrl;
    @Value("${app.webrtc.turn-url}")
    private String turnUrl;
    @Value("${app.webrtc.turn-username}")
    private String turnUsername;
    @Value("${app.webrtc.turn-password}")
    private String turnPassword;

    private static final String ACTIVE_PARTICIPANTS_PREFIX = "active:participants:";

    @Override
    public MediaJoinResponse prepareMediaConnection(String meetingCode) {
        String internalUserId = UserContext.getUserId();

        // 1. Tìm thông tin phòng để xác định ai là Host
        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp"));

        boolean isHost = meeting.getHostId().equals(internalUserId);

        // 2. 🛡️ ZERO-TRUST CHECK: ĐẶC QUYỀN CỦA HOST
        if (!isHost) {
            // Nếu KHÔNG PHẢI Host -> Phải qua cửa kiểm duyệt Redis
            String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;
            Double score = stringRedisTemplate.opsForZSet().score(activeKey, internalUserId);

            if (score == null) {
                log.error("🚨 CẢNH BÁO: Guest [{}] cố lấy Token Media phòng [{}] khi chưa được duyệt!", internalUserId, meetingCode);
                throw new SecurityException("Bạn chưa được Host duyệt vào phòng!");
            }
        } else {
            // Nếu LÀ Host -> Mời sếp vào
            log.info("👑 Host [{}] đang khởi tạo luồng Media cho phòng [{}]", internalUserId, meetingCode);
        }

        // 3. Đóng gói cấu hình ICE (STUN/TURN)
        MediaJoinResponse.IceServerConfig iceConfig = MediaJoinResponse.IceServerConfig.builder()
                .stunUrl(stunUrl)
                .turnUrl(turnUrl)
                .username(turnUsername)
                .credential(turnPassword)
                .build();

        // 4. Sinh Token LiveKit
        String liveKitToken = mediaTokenService.generateLiveKitToken(meetingCode, internalUserId);

        return MediaJoinResponse.builder()
                .mode("SFU")
                .token(liveKitToken)
                .serverUrl(liveKitHost)
                .iceServers(iceConfig)
                .build();
    }
}