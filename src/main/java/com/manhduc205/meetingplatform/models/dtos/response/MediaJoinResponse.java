package com.manhduc205.meetingplatform.models.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaJoinResponse {
    private String mode;        // "P2P" hoặc "SFU"
    private String token;       // JWT của LiveKit
    private String serverUrl;   // Địa chỉ máy chủ LiveKit
    private IceServerConfig iceServers;
    @Data
    @Builder
    public static class IceServerConfig {
        private String stunUrl;
        private String turnUrl;
        private String username;
        private String credential;
    }
}