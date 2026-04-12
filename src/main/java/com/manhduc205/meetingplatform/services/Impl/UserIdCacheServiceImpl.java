package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.exceptions.ResourceNotFoundException;
import com.manhduc205.meetingplatform.models.UserEntity;
import com.manhduc205.meetingplatform.repositories.UserRepository;
import com.manhduc205.meetingplatform.services.UserIdCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserIdCacheServiceImpl implements UserIdCacheService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String USER_MAP_KEY_PREFIX = "user:map:keycloak:";

    @Override
    public String getOrResolveInternalId(String keycloakId) {
        String key = USER_MAP_KEY_PREFIX + keycloakId;

        Object cachedId = redisTemplate.opsForValue().get(key);
        if (cachedId != null) {
            return cachedId.toString();
        }

        // khi cache miss
        log.info("Cache miss: Đang query DB để resolve Keycloak ID [{}]", keycloakId);
        UserEntity user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Unregistered user trying to access resources."));

        String internalId = user.getId();

        // Lưu lại cache với TTL 12 giờ
        redisTemplate.opsForValue().set(key, internalId, 12, TimeUnit.HOURS);

        return internalId;
    }
}