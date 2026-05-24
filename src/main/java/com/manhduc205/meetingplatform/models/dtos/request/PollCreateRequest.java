package com.manhduc205.meetingplatform.models.dtos.request;

import lombok.Data;
import java.util.List;

@Data
public class PollCreateRequest {
    private String question;
    private List<String> options; // Danh sách text của các lựa chọn
    private Boolean isMultipleChoice;
}