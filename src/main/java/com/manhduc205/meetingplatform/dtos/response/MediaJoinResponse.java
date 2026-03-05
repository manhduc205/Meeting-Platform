package com.manhduc205.meetingplatform.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaJoinResponse {
    private String mode;        // "P2P" hoặc "SFU"
    private String token;       // JWT của LiveKit
    private String serverUrl;   // Địa chỉ máy chủ LiveKit
}