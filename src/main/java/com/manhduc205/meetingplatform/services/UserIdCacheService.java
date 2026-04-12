package com.manhduc205.meetingplatform.services;

public interface UserIdCacheService {
    String getOrResolveInternalId(String keycloakId);
}