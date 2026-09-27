package com.manhduc205.AI_application.controller;

import com.manhduc205.AI_application.dto.request.YoutubeSummaryRequest;
import com.manhduc205.AI_application.dto.response.YoutubeSummaryResponse;
import com.manhduc205.AI_application.dto.response.YoutubeTranscriptPageResponse;
import com.manhduc205.AI_application.service.YoutubeSummaryService;
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
@RequestMapping("/api/v1/youtube-summaries")
@RequiredArgsConstructor
public class YoutubeSummaryController {
    private final YoutubeSummaryService youtubeSummaryService;

    @PostMapping
    public ResponseEntity<YoutubeSummaryResponse> create(@Valid @RequestBody YoutubeSummaryRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(youtubeSummaryService.create(request));
    }

    @GetMapping("/{summaryId}")
    public YoutubeSummaryResponse get(@PathVariable String summaryId) {
        return youtubeSummaryService.get(summaryId);
    }

    @GetMapping("/{summaryId}/transcript")
    public YoutubeTranscriptPageResponse getTranscript(
            @PathVariable String summaryId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit) {
        return youtubeSummaryService.getTranscript(summaryId, cursor, limit);
    }
}
