package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.services.HeartbeatService;
import com.manhduc205.meetingplatform.services.MeetingPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HeartbeatServiceImpl implements HeartbeatService {
    private final MeetingPresenceService presenceService;

    @Override
    public boolean updateHeartbeat(String meetingCode, String userId, String sessionId) {
        return presenceService.refreshConnection(meetingCode, userId, sessionId);
    }

    @Override
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void cleanupExpiredHeartbeats() {
        presenceService.cleanupStaleConnections();
    }
}
