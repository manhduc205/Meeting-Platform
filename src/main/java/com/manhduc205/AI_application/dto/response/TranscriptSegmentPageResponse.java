package com.manhduc205.AI_application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegmentPageResponse {
    private List<Segment> items;
    private String nextCursor;
    private boolean hasNext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        private String id;
        private Long sequence;
        private Long startMs;
        private Long endMs;
        private String speakerId;
        private String speakerName;
        private String text;
        private Double confidence;
    }
}
