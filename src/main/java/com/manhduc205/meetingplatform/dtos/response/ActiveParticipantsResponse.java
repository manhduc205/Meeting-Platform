package com.manhduc205.meetingplatform.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Response để trả về danh sách những người đang họp
 * Optimized: Chỉ trả về những người đã được duyệt (ACTIVE)
 */
@Data
@Builder
public class ActiveParticipantsResponse {
    private Integer totalCount;              // Tổng số người
    private List<ParticipantDto> participants; // Danh sách participant (limited, VD: top 10)
    private String displayText;              // Text hiển thị: "Alex, Sarah, and 10 others are already here"
}

