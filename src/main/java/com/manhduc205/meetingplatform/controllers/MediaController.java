package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.response.MediaJoinResponse;
import com.manhduc205.meetingplatform.services.MediaTokenService;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaTokenService mediaTokenService;
    private final MeetingPresenceService presenceService;

    @Value("${app.livekit.host}")
    private String liveKitHost;

    @GetMapping("/join/{meetingCode}")
    public ResponseEntity<?> requestJoinMedia(@PathVariable String meetingCode, @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        boolean useSfu = presenceService.shouldSwitchToSfu(meetingCode);
        if(useSfu){
            String liveKitToken = mediaTokenService.generateLiveKitToken(meetingCode, userId);
            return ResponseEntity.ok(MediaJoinResponse.builder()
                    .mode("SFU")
                    .token(liveKitToken)
                    .serverUrl(liveKitHost)
                    .build());
        } else {
            return ResponseEntity.ok(MediaJoinResponse.builder()
                    .mode("P2P")
                    .build());
        }
    }

}
