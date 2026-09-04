package com.manhduc205.AI_application.entity;

import com.manhduc205.AI_application.enums.AiContentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;


@Document(collection = "recording_ai_contents")
@CompoundIndex(name = "uk_recording_ai_content_recording", def = "{'recordingId': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingAiContentDocument {
    @Id
    private String id;

    private Long recordingId;

    @Builder.Default
    private AiContentStatus transcriptStatus = AiContentStatus.NOT_REQUESTED;

    @Builder.Default
    private AiContentStatus summaryStatus = AiContentStatus.NOT_REQUESTED;

    private String sourceLanguage;
    private String summary;
    private List<KeyMoment> keyMoments;

    /** Immutable artefacts produced by an AI run, stored under the recording's MinIO prefix. */
    private String rawTranscriptObjectKey;
    private String captionObjectKey;
    private String summaryObjectKey;
    private String model;
    private Integer version;
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
