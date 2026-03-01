package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.dtos.request.UserUpdateRequest;
import com.manhduc205.meetingplatform.dtos.response.UserProfileResponse;
import com.manhduc205.meetingplatform.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        UserProfileResponse response = userService.getCurrentUserProfile(keycloakId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserUpdateRequest request) {

        String keycloakId = jwt.getSubject();
        UserProfileResponse response = userService.updateProfile(keycloakId, request);
        return ResponseEntity.ok(response);
    }
}