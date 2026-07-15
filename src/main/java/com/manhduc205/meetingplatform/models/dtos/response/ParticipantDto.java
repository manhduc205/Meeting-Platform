package com.manhduc205.meetingplatform.models.dtos.response;

import lombok.Builder;
import lombok.Data;

/**
 * Lightweight DTO for participant - chỉ chứa thông tin cần thiết
 * Để tối ưu hiệu năng và giảm dữ liệu truyền
 */
@Data
@Builder
public class ParticipantDto {
    private String id;           // User ID
    private String fullName;
    private String avatarUrl;    // Avatar
    private String status;       // WAITING, APPROVED, ACTIVE
    private Boolean isMe;
}

