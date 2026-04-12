package com.manhduc205.meetingplatform.dtos.response;


import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class PollResponse {
    private String id;
    private String question;
    private Boolean isMultipleChoice;
    private String status;
    private List<PollOptionDto> options;
    private Long totalVotes;
    private boolean hasVoted; // Trả về true nếu người gọi API đã vote rồi
    @Data @Builder
    public static class PollOptionDto {
        private String id;
        private String text;
        private long voteCount;
        private Boolean votedByMe;
    }
}
