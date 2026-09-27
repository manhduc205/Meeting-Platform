package com.manhduc205.AI_application.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "youtube_transcript_segments")
@CompoundIndex(name = "uk_youtube_summary_sequence", def = "{'summaryId': 1, 'sequence': 1}", unique = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeTranscriptSegmentDocument {
    @Id
    private String id;
    private String summaryId;
    private Long sequence;
    private Long startMs;
    private Long endMs;
    private String text;
}
