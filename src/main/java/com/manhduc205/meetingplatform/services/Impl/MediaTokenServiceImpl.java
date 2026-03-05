package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.services.MediaTokenService;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaTokenServiceImpl implements MediaTokenService {
    @Value("${app.livekit.api-key}")
    private String apiKey;

    @Value("${app.livekit.api-secret}")
    private String apiSecret;
    @Override
    public String generateLiveKitToken(String meetingCode, String userId) {
        AccessToken token = new AccessToken(apiKey, apiSecret);

        token.setName(userId);
        token.setIdentity(userId);

        token.addGrants(new RoomJoin(true), new RoomName(meetingCode));

        return token.toJwt();
    }
}
