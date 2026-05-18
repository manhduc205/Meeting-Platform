package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.response.RecordingResponse;
import com.manhduc205.meetingplatform.services.RecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/meetings/{meetingCode}/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;

    // Host gọi API này để bắt đầu
    @PostMapping("/start")
    public ResponseEntity<RecordingResponse> start(@PathVariable String meetingCode) {
        return ResponseEntity.ok(recordingService.startRecording(meetingCode));
    }

    // Host gọi API này để dừng
    @PostMapping("/{egressId}/stop")
    public ResponseEntity<Void> stop(@PathVariable String meetingCode, @PathVariable String egressId) {
        recordingService.stopRecording(meetingCode, egressId);
        return ResponseEntity.ok().build();
    }

    // Tất cả người tham gia đều có thể xem danh sách bản ghi
    @GetMapping
    public ResponseEntity<List<RecordingResponse>> getRecordings(@PathVariable String meetingCode) {
        return ResponseEntity.ok(recordingService.getMeetingRecordings(meetingCode));
    }
}