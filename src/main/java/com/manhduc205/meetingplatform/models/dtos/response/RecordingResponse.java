package com.manhduc205.meetingplatform.models.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class RecordingResponse {
    private Long id;
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
    private Instant createdAt;
    private Instant purgeAfter;
}
