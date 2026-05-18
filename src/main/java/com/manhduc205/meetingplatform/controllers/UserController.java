package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.request.UserUpdateRequest;
import com.manhduc205.meetingplatform.models.dtos.response.UserProfileResponse;
import com.manhduc205.meetingplatform.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    @GetMapping("/sync")
    public ResponseEntity<String> syncUser() {
        return ResponseEntity.ok("User synchronized successfully");
    }
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        UserProfileResponse response = userService.getCurrentUserProfile();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UserUpdateRequest request) {

        UserProfileResponse response = userService.updateProfile(request);
        return ResponseEntity.ok(response);
    }
}