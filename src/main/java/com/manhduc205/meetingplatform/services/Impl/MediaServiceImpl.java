package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.response.MediaJoinResponse;
import com.manhduc205.meetingplatform.services.MediaService;
import com.manhduc205.meetingplatform.services.MediaTokenService;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaTokenService mediaTokenService;
    private final MeetingPresenceService presenceService;

    @Value("${app.livekit.host}") private String liveKitHost;
    @Value("${app.webrtc.stun-url}") private String stunUrl;
    @Value("${app.webrtc.turn-url}") private String turnUrl;
    @Value("${app.webrtc.turn-username}") private String turnUsername;
    @Value("${app.webrtc.turn-password}") private String turnPassword;

    @Override
    public MediaJoinResponse prepareMediaConnection(String meetingCode, String userId) {
        log.info("Chuẩn bị hạ tầng mạng cho User [{}] tại phòng [{}]", userId, meetingCode);

        // Đóng gói cấu hình ICE (STUN/TURN) - Luôn cần thiết cho cả P2P và SFU
        MediaJoinResponse.IceServerConfig iceConfig = MediaJoinResponse.IceServerConfig.builder()
                .stunUrl(stunUrl)
                .turnUrl(turnUrl)
                .username(turnUsername)
                .credential(turnPassword)
                .build();

        boolean shouldSfu = presenceService.shouldSwitchToSfu(meetingCode);

        if (shouldSfu) {
            log.info("Phòng [{}] đủ điều kiện dùng SFU. Đang tiến hành cấp Token...", meetingCode);
            String liveKitToken = mediaTokenService.generateLiveKitToken(meetingCode, userId);

            return MediaJoinResponse.builder()
                    .mode("SFU")
                    .token(liveKitToken)
                    .serverUrl(liveKitHost)
                    .iceServers(iceConfig)
                    .build();
        } else {
            log.info("Phòng [{}] dùng P2P để tối ưu tài nguyên Server.", meetingCode);
            return MediaJoinResponse.builder()
                    .mode("P2P")
                    .iceServers(iceConfig)
                    .build();
        }
    }
}