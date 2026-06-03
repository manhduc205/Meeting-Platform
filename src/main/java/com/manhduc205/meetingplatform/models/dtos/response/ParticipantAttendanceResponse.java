package com.manhduc205.meetingplatform.models.dtos.response;

import com.manhduc205.meetingplatform.enums.ParticipantRole;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ParticipantAttendanceResponse {
    private String userId;
    private String fullName;
    private String avatar;
    private ParticipantRole role;
    private LocalDateTime joinedAt;
}