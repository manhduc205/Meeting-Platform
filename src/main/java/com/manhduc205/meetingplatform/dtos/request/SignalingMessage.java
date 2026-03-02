package com.manhduc205.meetingplatform.dtos.request;

import com.manhduc205.meetingplatform.enums.MessageCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// payload dạng Object cho linh hoạt
public class SignalingMessage {
    private MessageCategory category;    // Phân loại: PRESENCE, SIGNALING, ACTION
    private String type;        // Chi tiết: JOIN, LEAVE, OFFER, ANSWER, SWITCH_TO_SFU...
    private String senderId;    // Người gửi
    private String targetId;    // Người nhận (Chỉ dùng khi P2P gửi đích danh)
    private String meetingCode; // Mã phòng (xxx-xxx-xxx)
    private Object payload;     // Dữ liệu linh hoạt (Nội dung chat, chuỗi SDP kết nối video)
    private LocalDateTime timestamp;
}