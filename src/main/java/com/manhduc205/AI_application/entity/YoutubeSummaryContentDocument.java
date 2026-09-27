package com.manhduc205.AI_application.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "youtube_summary_contents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeSummaryContentDocument {
    @Id
    private String id;

    @Indexed(unique = true)
    private String summaryId;

    private String sourceLanguage;
    private String targetLanguage;
    private String summary;
    private List<KeyMoment> keyMoments;
    private String rawTranscriptObjectKey;
    private String translatedTranscriptObjectKey;
    private String captionObjectKey;
    private String summaryObjectKey;
    private String model;
    private Instant generatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyMoment {
        private Long startMs;
        private Long endMs;
        private String topic;
    }
}
