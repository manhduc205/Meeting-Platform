package com.manhduc205.AI_application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class RecordingDetailResponse {
    private Long id;
    private String egressId;
    private String status;
    private String visibility;
    private String title;
    private Author author;
    private Metadata metadata;
    private AiContent ai;
    private Transcript transcript;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Author {
        private String id;
        private String fullName;
        private String avatarUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private Instant createdAt;
        private Long durationSeconds;
        private String videoUrl;
        private String storagePrefix;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiContent {
        private String transcriptStatus;
        private String summaryStatus;
        private String sourceLanguage;
        private String summary;
        private List<KeyMoment> keyMoments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyMoment {
        private Long startMs;
        private Long endMs;
        private String topic;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transcript {
        private String status;
        private String language;
        private long totalSegments;
    }
}
