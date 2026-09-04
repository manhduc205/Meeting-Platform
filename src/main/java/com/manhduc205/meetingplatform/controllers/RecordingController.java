package com.manhduc205.meetingplatform.controllers;

import com.manhduc205.meetingplatform.models.dtos.response.RecordingResponse;
import com.manhduc205.meetingplatform.models.dtos.response.RecordingActionResponse;
import com.manhduc205.meetingplatform.services.RecordingDeletionService;
import com.manhduc205.meetingplatform.services.RecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;
    private final RecordingDeletionService recordingDeletionService;

    @GetMapping
    public ResponseEntity<List<RecordingResponse>> getAllMyRecordings() {
        return ResponseEntity.ok(recordingService.getAllAccessibleRecordingsForCurrentUser());
    }

    @GetMapping("/trash")
    public ResponseEntity<List<RecordingResponse>> getTrash() {
        return ResponseEntity.ok(recordingService.getTrashForCurrentUser());
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

    @DeleteMapping("/{recordingId}")
    public ResponseEntity<RecordingActionResponse> moveToTrash(@PathVariable Long recordingId) {
        Instant purgeAt = recordingDeletionService.moveToTrash(recordingId);
        return ResponseEntity.ok(new RecordingActionResponse(
                "Đã chuyển bản ghi vào thùng rác. Bản ghi sẽ tự xóa sau 3 ngày.",
                purgeAt));
    }

    @PostMapping("/{recordingId}/restore")
    public ResponseEntity<RecordingActionResponse> restore(@PathVariable Long recordingId) {
        recordingDeletionService.restore(recordingId);
        return ResponseEntity.ok(new RecordingActionResponse("Đã khôi phục bản ghi.", null));
    }

    @DeleteMapping("/{recordingId}/permanent")
    public ResponseEntity<RecordingActionResponse> deletePermanently(@PathVariable Long recordingId) {
        Instant purgeAt = recordingDeletionService.requestPermanentDeletion(recordingId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RecordingActionResponse(
                "Đã tiếp nhận yêu cầu xóa vĩnh viễn. Video sẽ được xóa khỏi MinIO trong ít phút.",
                purgeAt));
    }
}
