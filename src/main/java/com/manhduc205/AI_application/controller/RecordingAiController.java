package com.manhduc205.AI_application.controller;

import com.manhduc205.AI_application.dto.request.TranscriptRequestRequest;
import com.manhduc205.AI_application.dto.request.RecordingChatRequest;
import com.manhduc205.AI_application.dto.response.ArtifactUrlResponse;
import com.manhduc205.AI_application.dto.response.RecordingDetailResponse;
import com.manhduc205.AI_application.dto.response.RecordingSummaryResponse;
import com.manhduc205.AI_application.dto.response.TranscriptRequestResponse;
import com.manhduc205.AI_application.dto.response.TranscriptSegmentPageResponse;
import com.manhduc205.AI_application.service.RecordingAiJobService;
import com.manhduc205.AI_application.service.RecordingAiInteractionService;
import com.manhduc205.AI_application.service.RecordingContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingAiController {
    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final RecordingContentService recordingContentService;
    private final RecordingAiJobService recordingAiJobService;
    private final RecordingAiInteractionService recordingAiInteractionService;

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

    @GetMapping("/{recordingId}/summary")
    public ResponseEntity<RecordingSummaryResponse> getSummary(@PathVariable Long recordingId) {
        return ResponseEntity.ok(recordingAiInteractionService.getSummary(recordingId));
    }

    @DeleteMapping("/{recordingId}/summary")
    public ResponseEntity<Void> deleteSummary(@PathVariable Long recordingId) {
        recordingAiInteractionService.deleteSummary(recordingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{recordingId}/summary/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> streamSummary(@PathVariable Long recordingId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(TEXT_PLAIN_UTF8)
                .body(recordingAiInteractionService.streamSummary(recordingId));
    }

    @PostMapping(value = "/{recordingId}/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> streamChat(
            @PathVariable Long recordingId,
            @Valid @RequestBody RecordingChatRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(TEXT_PLAIN_UTF8)
                .body(recordingAiInteractionService.streamChat(recordingId, request));
    }

    @GetMapping("/{recordingId}/artifacts/{type}/url")
    public ResponseEntity<ArtifactUrlResponse> createArtifactUrl(
            @PathVariable Long recordingId,
            @PathVariable String type) {
        return ResponseEntity.ok(recordingAiInteractionService.createArtifactUrl(recordingId, type));
    }
}
