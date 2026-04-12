package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.dtos.response.MediaJoinResponse;
import com.manhduc205.meetingplatform.services.MediaService;
import com.manhduc205.meetingplatform.services.MediaTokenService;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaTokenService mediaTokenService;

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

    @Override
    public MediaJoinResponse prepareMediaConnection(String meetingCode ) {
        String internalUserId = UserContext.getUserId();
        // Đóng gói cấu hình ICE (STUN/TURN) - Luôn cần thiết cho cả P2P và SFU
        MediaJoinResponse.IceServerConfig iceConfig = MediaJoinResponse.IceServerConfig.builder()
                .stunUrl(stunUrl)
                .turnUrl(turnUrl)
                .username(turnUsername)
                .credential(turnPassword)
                .build();
        String liveKitToken = mediaTokenService.generateLiveKitToken(meetingCode, internalUserId);
        return MediaJoinResponse.builder()
                .mode("SFU")
                .token(liveKitToken)
                .serverUrl(liveKitHost)
                .iceServers(iceConfig)
                .build();
    }
}