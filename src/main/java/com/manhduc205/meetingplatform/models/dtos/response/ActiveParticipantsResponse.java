package com.manhduc205.meetingplatform.models.dtos.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;


@Data
@Builder
public class ActiveParticipantsResponse {
    private Integer totalCount;
    private List<ParticipantDto> participants;
    private ParticipantDto currentUser;
    private String displayText;              // Text hiển thị: "Alex, Sarah, and 10 others are already here"
}

