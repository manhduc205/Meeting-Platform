package com.manhduc205.meetingplatform.dtos.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaisedHandResponse {
    private String meetingCode;
    private int totalCount;
    private List<ParticipantDto> participants;
}