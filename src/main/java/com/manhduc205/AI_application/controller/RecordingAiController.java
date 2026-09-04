package com.manhduc205.AI_application.controller;

import com.manhduc205.AI_application.dto.request.TranscriptRequestRequest;
import com.manhduc205.AI_application.dto.response.RecordingDetailResponse;
import com.manhduc205.AI_application.dto.response.TranscriptRequestResponse;
import com.manhduc205.AI_application.dto.response.TranscriptSegmentPageResponse;
import com.manhduc205.AI_application.service.RecordingAiJobService;
import com.manhduc205.AI_application.service.RecordingContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingAiController {
    private final RecordingContentService recordingContentService;
    private final RecordingAiJobService recordingAiJobService;

    @GetMapping("/{recordingId}")
    public ResponseEntity<RecordingDetailResponse> getRecordingDetail(@PathVariable Long recordingId) {
        return ResponseEntity.ok(recordingContentService.getRecordingDetail(recordingId));
    }

    @GetMapping({"/{recordingId}/transcript", "/{recordingId}/transcript/segments"})
    public ResponseEntity<TranscriptSegmentPageResponse> getTranscript(
            @PathVariable Long recordingId,
            @RequestParam String language,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(recordingContentService.getTranscriptSegments(recordingId, language, cursor, limit));
    }

    @PostMapping("/{recordingId}/transcript-requests")
    public ResponseEntity<TranscriptRequestResponse> requestTranscript(
            @PathVariable Long recordingId,
            @Valid @RequestBody(required = false) TranscriptRequestRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(recordingAiJobService.requestTranscript(recordingId, request));
    }
}
