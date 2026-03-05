package com.manhduc205.meetingplatform.services;

public interface MediaTokenService {
    String generateLiveKitToken(String meetingCode, String userId);
}