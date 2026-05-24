package com.manhduc205.meetingplatform.models.dtos.response;

import lombok.Builder;
import lombok.Data;

/**
 * Response khi user join phòng
 * Trả về status để client xác định next action
 */
@Data
@Builder
public class JoinMeetingResponse {
    private String meetingCode;      // Mã phòng
    private String userId;           // User ID
    private String status;           // APPROVED, WAITING (nhánh logic)
    private String message;          // Message hướng dẫn người dùng
}

