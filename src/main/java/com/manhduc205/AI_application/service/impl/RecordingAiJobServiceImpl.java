package com.manhduc205.AI_application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.meetingplatform.enums.OutboxEventType;
import com.manhduc205.AI_application.enums.RecordingAiJobStatus;
import com.manhduc205.meetingplatform.enums.RecordingStatus;
import com.manhduc205.routing.dto.TranscriptRequestedMessage;
import com.manhduc205.meetingplatform.models.OutboxEventEntity;
import com.manhduc205.AI_application.entity.RecordingAiJobEntity;
import com.manhduc205.meetingplatform.models.RecordingEntity;
import com.manhduc205.AI_application.dto.request.TranscriptRequestRequest;
import com.manhduc205.AI_application.dto.response.TranscriptRequestResponse;
import com.manhduc205.meetingplatform.repositories.OutboxEventRepository;
import com.manhduc205.AI_application.repository.RecordingAiJobRepository;
import com.manhduc205.meetingplatform.repositories.RecordingRepository;
import com.manhduc205.AI_application.service.RecordingAiJobService;
import com.manhduc205.meetingplatform.utils.RecordingStoragePaths;
import com.manhduc205.meetingplatform.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordingAiJobServiceImpl implements RecordingAiJobService {
    static final String OPERATION = "TRANSCRIPT_SUMMARY";
    private static final List<RecordingAiJobStatus> ACTIVE_STATUSES = List.of(
            RecordingAiJobStatus.REQUESTED,
            RecordingAiJobStatus.PUBLISHED
    );

    private final RecordingRepository recordingRepository;
    private final RecordingAiJobRepository jobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TranscriptRequestResponse requestTranscript(Long recordingId, TranscriptRequestRequest request) {
        String currentUserId = UserContext.getUserId();
        recordingRepository.findAccessibleRecordingById(currentUserId, recordingId)
                .orElseThrow(() -> new SecurityException("Bạn không có quyền xử lý bản ghi này hoặc bản ghi không tồn tại"));
        RecordingEntity recording = recordingRepository.findByIdForUpdate(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi"));

        if (recording.getStatus() != RecordingStatus.COMPLETED) {
            throw new IllegalStateException("Chỉ có thể tạo transcript sau khi bản ghi đã hoàn tất");
        }
        if (recording.getStoragePrefix() == null || recording.getStoragePrefix().isBlank()) {
            throw new IllegalStateException("Bản ghi chưa có thư mục lưu trữ chuẩn để xử lý transcript");
        }

        var activeJob = jobRepository.findFirstByRecordingIdAndOperationAndStatusInOrderByVersionDesc(
                recordingId, OPERATION, ACTIVE_STATUSES);
        if (activeJob.isPresent()) return toResponse(activeJob.get());

        int version = jobRepository.findFirstByRecordingIdAndOperationOrderByVersionDesc(recordingId, OPERATION)
                .map(previous -> previous.getVersion() + 1)
                .orElse(1);
        String language = request == null || request.language() == null || request.language().isBlank()
                ? "vi"
                : request.language();
        Instant now = Instant.now();
        RecordingAiJobEntity job = jobRepository.save(RecordingAiJobEntity.builder()
                .id(UUID.randomUUID().toString())
                .recordingId(recordingId)
                .requestedBy(currentUserId)
                .operation(OPERATION)
                .status(RecordingAiJobStatus.REQUESTED)
                .language(language)
                .version(version)
                .requestedAt(now)
                .build());

        String messageId = UUID.randomUUID().toString();
        TranscriptRequestedMessage command = new TranscriptRequestedMessage(
                messageId,
                "transcript.requested",
                job.getId(),
                recordingId,
                recording.getStoragePrefix(),
                RecordingStoragePaths.videoSource(recording.getStoragePrefix()),
                RecordingStoragePaths.rawTranscript(recording.getStoragePrefix(), version),
                RecordingStoragePaths.caption(recording.getStoragePrefix(), language, version),
                RecordingStoragePaths.summary(recording.getStoragePrefix(), language, version),
                "auto",
                language,
                currentUserId,
                version,
                now
        );
        outboxEventRepository.save(OutboxEventEntity.builder()
                .id(messageId)
                .eventType(OutboxEventType.TRANSCRIPT_REQUESTED)
                .aggregateId(job.getId())
                .payload(writeJson(command))
                .build());
        return toResponse(job);
    }

    @Override
    @Transactional
    public void markPublished(String jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == RecordingAiJobStatus.REQUESTED) {
                job.setStatus(RecordingAiJobStatus.PUBLISHED);
                job.setPublishedAt(Instant.now());
            }
        });
    }

    @Override
    @Transactional
    public void markInfrastructureFailed(String jobId, String error) {
        if (jobId == null || jobId.isBlank()) return;
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == RecordingAiJobStatus.COMPLETED
                    || job.getStatus() == RecordingAiJobStatus.FAILED) {
                return;
            }
            job.setStatus(RecordingAiJobStatus.FAILED);
            String message = error == null || error.isBlank()
                    ? "RabbitMQ đã chuyển message vào DLQ cuối"
                    : error;
            job.setLastError(message.substring(0, Math.min(message.length(), 2000)));
            job.setCompletedAt(Instant.now());
        });
    }

    private String writeJson(TranscriptRequestedMessage command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tạo message xử lý transcript", exception);
        }
    }

    private TranscriptRequestResponse toResponse(RecordingAiJobEntity job) {
        return TranscriptRequestResponse.builder()
                .jobId(job.getId())
                .recordingId(job.getRecordingId())
                .status(job.getStatus().name())
                .language(job.getLanguage())
                .version(job.getVersion())
                .requestedAt(job.getRequestedAt())
                .build();
    }
}
