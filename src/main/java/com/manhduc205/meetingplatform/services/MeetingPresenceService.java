package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.dtos.request.SignalingMessage;

import java.util.List;
import java.util.Set;

public interface MeetingPresenceService {
    enum PresenceTransition {
        JOINED,
        RECONNECTED,
        RECONNECTING,
        OFFLINE,
        UNCHANGED
    }

    Set<String> getOnlineUsers(String meetingCode);
    void handleActionMessage(SignalingMessage message);
    List<SignalingMessage> handlePresenceUpdate(SignalingMessage message, String sessionId);
    PresenceTransition markConnectionAsReconnecting(String meetingCode, String userId, String sessionId);
    boolean refreshConnection(String meetingCode, String userId, String sessionId);
    void cleanupStaleConnections();
    void removeAllUserConnections(String meetingCode, String userId);
}
