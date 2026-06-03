package com.manhduc205.meetingplatform.models.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class RecordingResponse {
    private String egressId;
    private String meetingCode;
    private String recordingName;
    private String hostId;
    private String hostName;
    private String hostAvatar;
    private String status;
    private String visibility;
    private String thumbnailUrl;
    private String fileUrl;
    private Long duration;
    private LocalDateTime createdAt;
}
