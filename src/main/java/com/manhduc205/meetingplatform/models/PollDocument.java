package com.manhduc205.meetingplatform.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "polls")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollDocument {
    @Id
    private String id;
    private String meetingCode;
    private String question;
    private Boolean isMultipleChoice;
    private String createdBy;
    private String status;
    private LocalDateTime createdAt;
    private List<PollOption> options;
}
