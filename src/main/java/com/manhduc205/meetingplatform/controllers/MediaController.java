package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.response.MediaJoinResponse;
import com.manhduc205.meetingplatform.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @GetMapping("/join/{meetingCode}")
    public ResponseEntity<MediaJoinResponse> requestJoinMedia(
            @PathVariable String meetingCode) {
        MediaJoinResponse response = mediaService.prepareMediaConnection(meetingCode);

        return ResponseEntity.ok(response);
    }
}