package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.models.dtos.request.UserUpdateRequest;
import com.manhduc205.meetingplatform.models.dtos.response.UserProfileResponse;
import com.manhduc205.meetingplatform.exceptions.DataNotFoundException;
import com.manhduc205.meetingplatform.models.UserEntity;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.UserService;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void syncUserFromJwt(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String picture = jwt.getClaimAsString("picture");

        userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {
                    log.info("ServiceImpl: Tạo mới tài khoản tự động từ SSO - Email: {}", email);
                    return userRepository.save(UserEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .keycloakId(keycloakId)
                            .email(email != null ? email : "no-email@sso.local")
                            .fullName(name)
                            .avatarUrl(picture)
                            .createdAt(LocalDateTime.now())
                            .build());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        String internalUserId = UserContext.getUserId();
        return userRepository.findById(internalUserId)
                .map(this::mapToProfileResponse)
                .orElseThrow(() -> {
                    log.error("ServiceImpl Lỗi: Không tìm thấy User với ID: {}", internalUserId);
                    return new DataNotFoundException("Người dùng không tồn tại trong hệ thống Database!");
                });
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile( UserUpdateRequest request) {
        String internalUserId = UserContext.getUserId();
        UserEntity user = userRepository.findById(internalUserId)
                .orElseThrow(() -> {
                    log.error("ServiceImpl Lỗi: Cập nhật thất bại. Không tìm thấy User ID: {}", internalUserId);
                    return new DataNotFoundException("Người dùng không tồn tại trong hệ thống Database!");
                });

        Optional.ofNullable(request.getFullName()).ifPresent(user::setFullName);
        Optional.ofNullable(request.getAvatarUrl()).ifPresent(user::setAvatarUrl);

        userRepository.save(user);
        log.info("ServiceImpl: Cập nhật Profile thành công cho User Email: {}", user.getEmail());

        return mapToProfileResponse(user);
    }

    private UserProfileResponse mapToProfileResponse(UserEntity entity) {
        return UserProfileResponse.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .avatarUrl(entity.getAvatarUrl())
                .build();
    }
}