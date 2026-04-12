package com.manhduc205.meetingplatform.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "poll_votes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "unique_vote_idx", def = "{'pollId': 1, 'userId': 1}", unique = true)
public class PollVoteLog {
    @Id
    private String id;
    private String pollId;
    private String optionId;
    private String userId;
    private LocalDateTime votedAt;
}