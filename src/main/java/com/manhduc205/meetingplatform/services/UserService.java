package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.dtos.request.UserUpdateRequest;
import com.manhduc205.meetingplatform.dtos.response.UserProfileResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {

    void syncUserFromJwt(Jwt jwt);

    UserProfileResponse getCurrentUserProfile(String keycloakId);
    UserProfileResponse updateProfile(String keycloakId, UserUpdateRequest request);
}