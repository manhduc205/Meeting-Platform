package com.manhduc205.AI_application.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
import com.manhduc205.AI_application.service.RecordingAiResultService;
import com.manhduc205.meetingplatform.utils.RecordingStoragePaths;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RecordingAiResultServiceImpl implements RecordingAiResultService {
    private static final int TRANSCRIPT_BATCH_SIZE = 500;

    private final RecordingAiJobRepository jobRepository;
    private final RecordingRepository recordingRepository;
    private final RecordingAiContentMongoRepository aiContentRepository;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;

    @Value("${app.minio.bucket}")
    private String minioBucket;

    @Value("${app.minio.max-transcript-bytes:52428800}")
    private int maxTranscriptBytes;

    @Override
    @Transactional
    public void handleCompleted(TranscriptCompletedMessage message) throws Exception {
        if (message.schemaVersion() != null && message.schemaVersion() != 1) {
            throw new IllegalArgumentException("schemaVersion transcript không được hỗ trợ");
        }
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

        TranscriptImport imported = importTranscript(message.rawTranscriptObjectKey(), job);
        if (message.segmentCount() != null && message.segmentCount() != imported.segmentCount()) {
            throw new IllegalArgumentException("segmentCount không khớp file transcript trên MinIO");
        }
        if (message.transcriptSha256() != null && !message.transcriptSha256().isBlank()
                && !message.transcriptSha256().equalsIgnoreCase(imported.sha256())) {
            throw new IllegalArgumentException("SHA-256 của transcript không khớp event");
        }

        RecordingAiContentDocument content = aiContentRepository.findByRecordingId(job.getRecordingId())
                .orElseGet(() -> RecordingAiContentDocument.builder().recordingId(job.getRecordingId()).build());
        content.setTranscriptStatus(AiContentStatus.READY);
        content.setSummaryStatus(AiContentStatus.NOT_REQUESTED);
        content.setSourceLanguage(job.getLanguage());
        content.setSummary(null);
        content.setKeyMoments(List.of());
        content.setRawTranscriptObjectKey(message.rawTranscriptObjectKey());
        content.setCaptionObjectKey(message.captionObjectKey());
        content.setSummaryObjectKey(expectedSummaryKey);
        content.setTranscriptSha256(imported.sha256());
        content.setSummarySha256(null);
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
        if (message.schemaVersion() != null && message.schemaVersion() != 1) {
            throw new IllegalArgumentException("schemaVersion transcript không được hỗ trợ");
        }
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
        content.setSummaryStatus(AiContentStatus.NOT_REQUESTED);
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

    private TranscriptImport importTranscript(String objectKey, RecordingAiJobEntity job) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream source = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioBucket)
                .object(objectKey)
                .build());
             DigestInputStream digested = new DigestInputStream(source, digest);
             InputStream input = new LimitedInputStream(digested, maxTranscriptBytes);
             JsonParser parser = objectMapper.getFactory().createParser(input)) {
            int count = streamSegments(parser, job);
            while (parser.nextToken() != null) parser.skipChildren();
            return new TranscriptImport(count, HexFormat.of().formatHex(digest.digest()));
        }
    }

    int streamSegments(JsonParser parser, RecordingAiJobEntity job) throws Exception {
        JsonToken token = parser.nextToken();
        if (token == JsonToken.START_OBJECT) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                token = parser.nextToken();
                if ("segments".equals(fieldName)) break;
                parser.skipChildren();
            }
        }
        if (token != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException("Artifact transcript thiếu mảng segments");
        }

        List<RecordingTranscriptSegmentDocument> batch = new ArrayList<>(TRANSCRIPT_BATCH_SIZE);
        int sequence = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            RawTranscriptSegment segment = objectMapper.readValue(parser, RawTranscriptSegment.class);
            validateSegment(segment);
            batch.add(toDocument(segment, sequence++, job));
            if (batch.size() == TRANSCRIPT_BATCH_SIZE) {
                upsertBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) upsertBatch(batch);
        if (sequence == 0) throw new IllegalArgumentException("Transcript không có segment");
        return sequence;
    }

    private void validateSegment(RawTranscriptSegment segment) {
        if (segment.start() == null || segment.end() == null || segment.start() < 0
                || segment.end() < segment.start() || segment.text() == null || segment.text().isBlank()) {
            throw new IllegalArgumentException("File transcript chứa segment không hợp lệ");
        }
    }

    private void upsertBatch(List<RecordingTranscriptSegmentDocument> batch) {
        BulkOperations operations = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, RecordingTranscriptSegmentDocument.class);
        for (RecordingTranscriptSegmentDocument segment : batch) {
            Query key = Query.query(Criteria.where("recordingId").is(segment.getRecordingId())
                    .and("language").is(segment.getLanguage())
                    .and("version").is(segment.getVersion())
                    .and("sequence").is(segment.getSequence()));
            Update value = new Update()
                    .set("startMs", segment.getStartMs())
                    .set("endMs", segment.getEndMs())
                    .set("originalText", segment.getOriginalText())
                    .set("translatedText", segment.getTranslatedText())
                    .unset("speakerId")
                    .unset("speakerName")
                    .unset("confidence");
            operations.upsert(key, value);
        }
        operations.execute();
    }

    private RecordingTranscriptSegmentDocument toDocument(
            RawTranscriptSegment segment, int sequence, RecordingAiJobEntity job) {
        return RecordingTranscriptSegmentDocument.builder()
                .recordingId(job.getRecordingId())
                .language(job.getLanguage())
                .sequence((long) sequence)
                .startMs(Math.round(segment.start() * 1000))
                .endMs(Math.round(segment.end() * 1000))
                .originalText(segment.text())
                .version(job.getVersion())
                .build();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "AI worker báo xử lý thất bại";
        return value.substring(0, Math.min(value.length(), 2000));
    }

    private record TranscriptImport(int segmentCount, String sha256) {
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) checkLimit(1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) checkLimit(read);
            return read;
        }

        private void checkLimit(int read) throws IOException {
            count += read;
            if (count > limit) throw new IOException("File transcript vượt quá giới hạn cho phép");
        }
    }
}
