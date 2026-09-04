package com.manhduc205.AI_application.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.AI_application.enums.AiContentStatus;
import com.manhduc205.AI_application.enums.RecordingAiJobStatus;
import com.manhduc205.routing.dto.RawTranscriptSegment;
import com.manhduc205.routing.dto.TranscriptCompletedMessage;
import com.manhduc205.routing.dto.TranscriptFailedMessage;
import com.manhduc205.AI_application.entity.RecordingAiContentDocument;
import com.manhduc205.AI_application.entity.RecordingAiJobEntity;
import com.manhduc205.meetingplatform.models.RecordingEntity;
import com.manhduc205.AI_application.entity.RecordingTranscriptSegmentDocument;
import com.manhduc205.AI_application.repository.RecordingAiContentMongoRepository;
import com.manhduc205.AI_application.repository.RecordingAiJobRepository;
import com.manhduc205.meetingplatform.repositories.RecordingRepository;
import com.manhduc205.AI_application.repository.RecordingTranscriptSegmentMongoRepository;
import com.manhduc205.AI_application.service.RecordingAiResultService;
import com.manhduc205.meetingplatform.utils.RecordingStoragePaths;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RecordingAiResultServiceImpl implements RecordingAiResultService {
    private final RecordingAiJobRepository jobRepository;
    private final RecordingRepository recordingRepository;
    private final RecordingAiContentMongoRepository aiContentRepository;
    private final RecordingTranscriptSegmentMongoRepository transcriptRepository;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${app.minio.bucket}")
    private String minioBucket;

    @Value("${app.minio.max-transcript-bytes:52428800}")
    private int maxTranscriptBytes;

    @Override
    @Transactional
    public void handleCompleted(TranscriptCompletedMessage message) throws Exception {
        RecordingAiJobEntity job = validateJob(message.jobId(), message.recordingId(), message.version());
        if (shouldIgnoreResult(job)) return;
        if (job.getStatus() == RecordingAiJobStatus.COMPLETED) return;

        RecordingEntity recording = recordingRepository.findById(message.recordingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi của AI result"));
        String expectedRawKey = RecordingStoragePaths.rawTranscript(recording.getStoragePrefix(), job.getVersion());
        String expectedCaptionKey = RecordingStoragePaths.caption(recording.getStoragePrefix(), job.getLanguage(), job.getVersion());
        String expectedSummaryKey = RecordingStoragePaths.summary(recording.getStoragePrefix(), job.getLanguage(), job.getVersion());
        if (!expectedRawKey.equals(message.rawTranscriptObjectKey())) {
            throw new IllegalArgumentException("rawTranscriptObjectKey không thuộc job hiện tại");
        }
        if (message.captionObjectKey() != null && !expectedCaptionKey.equals(message.captionObjectKey())) {
            throw new IllegalArgumentException("captionObjectKey không thuộc job hiện tại");
        }
        if (message.summaryObjectKey() != null && !expectedSummaryKey.equals(message.summaryObjectKey())) {
            throw new IllegalArgumentException("summaryObjectKey không thuộc job hiện tại");
        }

        List<RawTranscriptSegment> rawSegments = readTranscript(message.rawTranscriptObjectKey());
        if (message.segmentCount() != null && message.segmentCount() != rawSegments.size()) {
            throw new IllegalArgumentException("segmentCount không khớp file transcript trên MinIO");
        }
        validateSegments(rawSegments);

        List<RecordingTranscriptSegmentDocument> segments = IntStream.range(0, rawSegments.size())
                .mapToObj(index -> toDocument(rawSegments.get(index), index, job))
                .toList();
        transcriptRepository.deleteByRecordingIdAndLanguage(job.getRecordingId(), job.getLanguage());
        transcriptRepository.saveAll(segments);

        RecordingAiContentDocument content = aiContentRepository.findByRecordingId(job.getRecordingId())
                .orElseGet(() -> RecordingAiContentDocument.builder().recordingId(job.getRecordingId()).build());
        content.setTranscriptStatus(AiContentStatus.READY);
        content.setSummaryStatus(message.summary() == null || message.summary().isBlank()
                ? AiContentStatus.FAILED
                : AiContentStatus.READY);
        content.setSourceLanguage(job.getLanguage());
        content.setSummary(message.summary());
        content.setKeyMoments(toKeyMoments(message.keyMoments()));
        content.setRawTranscriptObjectKey(message.rawTranscriptObjectKey());
        content.setCaptionObjectKey(message.captionObjectKey());
        content.setSummaryObjectKey(message.summaryObjectKey());
        content.setModel(message.model());
        content.setVersion(job.getVersion());
        content.setGeneratedAt(message.completedAt() == null ? Instant.now() : message.completedAt());
        aiContentRepository.save(content);

        job.setStatus(RecordingAiJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        job.setLastError(null);
    }

    @Override
    @Transactional
    public void handleFailed(TranscriptFailedMessage message) {
        RecordingAiJobEntity job = validateJob(message.jobId(), message.recordingId(), message.version());
        if (shouldIgnoreResult(job)) return;
        if (job.getStatus() == RecordingAiJobStatus.COMPLETED) return;
        String error = message.errorCode() == null ? message.errorMessage()
                : message.errorCode() + ": " + message.errorMessage();
        job.setStatus(RecordingAiJobStatus.FAILED);
        job.setLastError(truncate(error));
        job.setCompletedAt(message.failedAt() == null ? Instant.now() : message.failedAt());

        RecordingAiContentDocument content = aiContentRepository.findByRecordingId(job.getRecordingId())
                .orElseGet(() -> RecordingAiContentDocument.builder().recordingId(job.getRecordingId()).build());
        content.setTranscriptStatus(AiContentStatus.FAILED);
        content.setSummaryStatus(AiContentStatus.FAILED);
        content.setSourceLanguage(job.getLanguage());
        content.setVersion(job.getVersion());
        aiContentRepository.save(content);
    }

    private RecordingAiJobEntity validateJob(String jobId, Long recordingId, Integer version) {
        if (jobId == null || recordingId == null || version == null) {
            throw new IllegalArgumentException("AI result thiếu jobId, recordingId hoặc version");
        }
        RecordingAiJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy recording AI job"));
        if (!Objects.equals(job.getRecordingId(), recordingId) || !Objects.equals(job.getVersion(), version)) {
            throw new IllegalArgumentException("AI result không khớp recording/version của job");
        }
        return job;
    }

    private boolean shouldIgnoreResult(RecordingAiJobEntity job) {
        return recordingRepository.findByIdForUpdate(job.getRecordingId())
                .map(recording -> recording.getPurgeAfter() != null)
                .orElse(true);
    }

    private List<RawTranscriptSegment> readTranscript(String objectKey) throws Exception {
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioBucket)
                .object(objectKey)
                .build())) {
            byte[] bytes = input.readNBytes(maxTranscriptBytes + 1);
            if (bytes.length > maxTranscriptBytes) {
                throw new IllegalArgumentException("File transcript vượt quá giới hạn cho phép");
            }
            return parseTranscript(bytes);
        }
    }

    List<RawTranscriptSegment> parseTranscript(byte[] bytes) throws Exception {
        JsonNode root = objectMapper.readTree(bytes);
        JsonNode segments = root.isArray() ? root : root.path("segments");
        if (!segments.isArray()) {
            throw new IllegalArgumentException("Artifact transcript thiếu mảng segments");
        }
        return objectMapper.convertValue(segments, new TypeReference<>() {});
    }

    private void validateSegments(List<RawTranscriptSegment> segments) {
        for (RawTranscriptSegment segment : segments) {
            if (segment.start() == null || segment.end() == null || segment.start() < 0
                    || segment.end() < segment.start() || segment.text() == null || segment.text().isBlank()) {
                throw new IllegalArgumentException("File transcript chứa segment không hợp lệ");
            }
        }
    }

    private RecordingTranscriptSegmentDocument toDocument(
            RawTranscriptSegment segment, int sequence, RecordingAiJobEntity job) {
        return RecordingTranscriptSegmentDocument.builder()
                .recordingId(job.getRecordingId())
                .language(job.getLanguage())
                .sequence((long) sequence)
                .startMs(Math.round(segment.start() * 1000))
                .endMs(Math.round(segment.end() * 1000))
                .speakerId(segment.speakerId())
                .speakerName(segment.speakerName())
                .originalText(segment.text())
                .confidence(segment.confidence())
                .version(job.getVersion())
                .build();
    }

    private List<RecordingAiContentDocument.KeyMoment> toKeyMoments(List<TranscriptCompletedMessage.KeyMoment> moments) {
        if (moments == null) return List.of();
        return moments.stream()
                .filter(moment -> moment.startMs() != null && moment.topic() != null && !moment.topic().isBlank())
                .map(moment -> RecordingAiContentDocument.KeyMoment.builder()
                        .startMs(moment.startMs())
                        .endMs(moment.endMs())
                        .topic(moment.topic())
                        .build())
                .toList();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "AI worker báo xử lý thất bại";
        return value.substring(0, Math.min(value.length(), 2000));
    }
}
