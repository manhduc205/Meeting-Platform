package com.manhduc205.AI_application.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** One document per spoken segment: efficient partial edits and cursor pagination. */
@Document(collection = "recording_transcript_segments")
@CompoundIndexes({
        @CompoundIndex(name = "uk_recording_language_sequence", def = "{'recordingId': 1, 'language': 1, 'sequence': 1}", unique = true),
        @CompoundIndex(name = "idx_recording_language_start", def = "{'recordingId': 1, 'language': 1, 'startMs': 1, 'sequence': 1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingTranscriptSegmentDocument {
    @Id
    private String id;

    private Long recordingId;
    private String language;
    private Long sequence;
    private Long startMs;
    private Long endMs;
    private String speakerId;
    private String speakerName;
    private String originalText;
    private String translatedText;
    private Double confidence;
    private Integer version;
}
