package com.manhduc205.meetingplatform.enums;

public enum ParticipantStatus {
    WAITING,      // Chờ host duyệt (khi waiting room enabled)
    APPROVED,     // Đã được duyệt, vào phòng
    REJECTED,     // Bị từ chối
    ACTIVE        // Đang họp
}

