package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.response.RecordingResponse;
import com.manhduc205.meetingplatform.services.RecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;

    @GetMapping
    public ResponseEntity<List<RecordingResponse>> getAllMyRecordings() {
        return ResponseEntity.ok(recordingService.getAllAccessibleRecordingsForCurrentUser());
    }

    @GetMapping("/meeting/{meetingCode}")
    public ResponseEntity<List<RecordingResponse>> getRecordingsByMeeting(@PathVariable String meetingCode) {
        return ResponseEntity.ok(recordingService.getMeetingRecordings(meetingCode));
    }

    @PostMapping("/meeting/{meetingCode}/start")
    public ResponseEntity<RecordingResponse> start(@PathVariable String meetingCode) {
        return ResponseEntity.ok(recordingService.startRecording(meetingCode));
    }

    @PostMapping("/meeting/{meetingCode}/stop")
    public ResponseEntity<Void> stop(@PathVariable String meetingCode, @RequestParam String egressId) {
        recordingService.stopRecording(meetingCode, egressId);
        return ResponseEntity.ok().build();
    }
}