package com.manhduc205.meetingplatform.services;

import java.util.Set;

public interface MeetingPresenceService {
    void addOnlineUser(String meetingCode, String userId);
    void removeOnlineUser(String meetingCode, String userId);
    Set<Object> getOnlineUsers(String meetingCode);
    boolean shouldSwitchToSfu(String meetingCode);
}