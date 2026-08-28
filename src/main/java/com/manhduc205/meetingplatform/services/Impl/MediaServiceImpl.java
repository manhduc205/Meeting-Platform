package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.models.dtos.response.MediaJoinResponse;
import com.manhduc205.meetingplatform.models.MeetingEntity;
import com.manhduc205.meetingplatform.enums.MeetingStatus;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.services.MediaService;
import com.manhduc205.meetingplatform.services.MediaTokenService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaTokenService mediaTokenService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeetingRepository meetingRepository;

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

    private static final String ACTIVE_PARTICIPANTS_PREFIX = "active:participants:";
    private static final String KICKED_PARTICIPANT_PREFIX = "meeting:kicked:";
    private static final long ACTIVE_PARTICIPANTS_TTL_HOURS = 12;

    @Override
    public MediaJoinResponse prepareMediaConnection(String meetingCode) {
        String internalUserId = UserContext.getUserId();

        MeetingEntity meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp"));

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
                throw new SecurityException("Bạn đã bị mời ra khỏi cuộc họp này");
            }

            String activeKey = ACTIVE_PARTICIPANTS_PREFIX + meetingCode;

            // 2. Nếu phòng tắt Waiting Room → Guest được vào thẳng, tự động ghi vào Redis
            if (Boolean.FALSE.equals(meeting.getIsWaitingRoomEnabled())) {
                Double existingScore = stringRedisTemplate.opsForZSet().score(activeKey, internalUserId);
                if (existingScore == null) {
                    stringRedisTemplate.opsForZSet().add(activeKey, internalUserId, System.currentTimeMillis());
                    stringRedisTemplate.expire(activeKey, ACTIVE_PARTICIPANTS_TTL_HOURS, TimeUnit.HOURS);
                    log.info("✅ Auto-approved guest [{}] vào phòng [{}] (Waiting Room đã tắt)", internalUserId, meetingCode);
                }
            } else {
                // 3. Phòng chờ đang bật → bắt buộc phải được Host duyệt trước
                Double score = stringRedisTemplate.opsForZSet().score(activeKey, internalUserId);
                if (score == null) {
                    log.error("CẢNH BÁO: Guest [{}] cố lấy Token Media phòng [{}] khi chưa được duyệt!",
                            internalUserId, meetingCode);
                    throw new SecurityException("Bạn chưa được Host duyệt vào phòng!");
                }
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
        String liveKitToken = mediaTokenService.generateLiveKitToken(meetingCode, internalUserId);

        return MediaJoinResponse.builder()
                .mode("SFU")
                .token(liveKitToken)
                .serverUrl(liveKitPublicUrl)
                .iceServers(iceConfig)
                .build();
    }
}
