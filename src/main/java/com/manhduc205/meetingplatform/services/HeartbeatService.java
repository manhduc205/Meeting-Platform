package com.manhduc205.meetingplatform.services;

/**
 * ✅ ENTERPRISE: Heartbeat Service Interface
 *
 * Theo dõi Ping-Pong heartbeat từ WebSocket clients
 * Tự động cleanup "Ghost Users" - những user mất kết nối
 */
public interface HeartbeatService {

    void updateHeartbeat(String meetingCode, String userId);

    void cleanupExpiredHeartbeats();
}

