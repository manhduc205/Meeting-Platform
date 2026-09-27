package com.manhduc205.AI_application.dto.response;

import java.util.List;

public record YoutubeTranscriptPageResponse(List<Segment> items, String nextCursor, boolean hasNext) {
    public record Segment(String id, Long sequence, Long startMs, Long endMs, String text) {
    }
}
