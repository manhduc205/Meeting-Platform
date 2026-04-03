package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.MeetingEntity;

public interface HostService {
    void updateSecuritySetting(String meetingCode, String requesterId, String type, boolean enabled);
    public void muteAll(String meetingCode, String requesterId);
    void kickUser(String meetingCode, String requesterId, String targetUserId);
}
