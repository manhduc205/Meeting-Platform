package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.dtos.request.SignalingMessage;

import java.util.List;
import java.util.Set;

public interface MeetingPresenceService {
    void addOnlineUser(String meetingCode, String userId);
    void removeOnlineUser(String meetingCode, String userId);
    Set<Object> getOnlineUsers(String meetingCode);
    boolean shouldSwitchToSfu(String meetingCode);
    // Trả về danh sách các tin nhắn cần Broadcast sau khi xử lý Join/Leave
    List<SignalingMessage> handlePresenceUpdate(SignalingMessage message);
    void markUserAsReconnecting(String meetingCode, String userId);
}