package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.dtos.request.UserUpdateRequest;
import com.manhduc205.meetingplatform.models.dtos.response.UserProfileResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {

    void syncUserFromJwt(Jwt jwt);

    UserProfileResponse getCurrentUserProfile();
    UserProfileResponse updateProfile(UserUpdateRequest request);
}