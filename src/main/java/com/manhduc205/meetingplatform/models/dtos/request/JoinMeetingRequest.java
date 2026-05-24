package com.manhduc205.meetingplatform.models.dtos.request;

import lombok.Data;

/**
 * Request khi user bấm nút "Join Now" để vào phòng họp
 */
@Data
public class JoinMeetingRequest {
    private String meetingCode;      // Mã phòng
    private String meetingPassword;  // Password (nếu có)
}

