package com.manhduc205.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawTranscriptSegment(
        Double start,
        Double end,
        String text,
        String speakerId,
        String speakerName,
        Double confidence
) {
}
