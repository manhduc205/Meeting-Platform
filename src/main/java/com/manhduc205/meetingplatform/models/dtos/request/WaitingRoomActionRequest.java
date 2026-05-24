package com.manhduc205.meetingplatform.models.dtos.request;

import com.manhduc205.meetingplatform.enums.WaitingRoomAction;
import lombok.Data;
import java.util.List;

@Data
public class WaitingRoomActionRequest {
    private WaitingRoomAction action;
    private List<String> userIds;
}