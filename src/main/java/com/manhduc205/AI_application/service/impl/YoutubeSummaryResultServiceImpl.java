package com.manhduc205.AI_application.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.AI_application.entity.YoutubeSummaryContentDocument;
import com.manhduc205.AI_application.entity.YoutubeSummaryEntity;
import com.manhduc205.AI_application.entity.YoutubeTranscriptSegmentDocument;
import com.manhduc205.AI_application.enums.YoutubeSummaryStatus;
import com.manhduc205.AI_application.repository.YoutubeSummaryContentMongoRepository;
import com.manhduc205.AI_application.repository.YoutubeSummaryRepository;
import com.manhduc205.AI_application.repository.YoutubeTranscriptSegmentMongoRepository;
import com.manhduc205.AI_application.service.YoutubeSummaryResultService;
import com.manhduc205.meetingplatform.utils.YoutubeSummaryStoragePaths;
import com.manhduc205.routing.dto.RawTranscriptSegment;
import com.manhduc205.routing.dto.YoutubeSummaryCompletedMessage;
import com.manhduc205.routing.dto.YoutubeSummaryFailedMessage;
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
public class YoutubeSummaryResultServiceImpl implements YoutubeSummaryResultService {
    private final YoutubeSummaryRepository summaryRepository;
    private final YoutubeSummaryContentMongoRepository contentRepository;
    private final YoutubeTranscriptSegmentMongoRepository transcriptRepository;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${app.minio.bucket}")
    private String minioBucket;

    @Value("${app.minio.max-transcript-bytes:52428800}")
    private int maxTranscriptBytes;

    @Override
    @Transactional
    public void handleCompleted(YoutubeSummaryCompletedMessage message) throws Exception {
        YoutubeSummaryEntity job = findJob(message.jobId());
        if (job.getStatus() == YoutubeSummaryStatus.COMPLETED) return;
        if (!Objects.equals(job.getVideoId(), message.videoId())
                || !Objects.equals(job.getTargetLanguage(), message.language())) {
            throw new IllegalArgumentException("Kết quả AI không khớp video hoặc ngôn ngữ yêu cầu");
        }

        String prefix = job.getStoragePrefix();
        requirePath(YoutubeSummaryStoragePaths.rawTranscript(prefix), message.rawTranscriptObjectKey(), "raw transcript");
        requirePath(YoutubeSummaryStoragePaths.caption(prefix, job.getTargetLanguage()), message.captionObjectKey(), "caption");
        requirePath(YoutubeSummaryStoragePaths.summary(prefix, job.getTargetLanguage()), message.summaryObjectKey(), "summary");
        if (message.translatedTranscriptObjectKey() != null) {
            requirePath(YoutubeSummaryStoragePaths.translatedTranscript(prefix, job.getTargetLanguage()),
                    message.translatedTranscriptObjectKey(), "translated transcript");
        }

        String displayTranscriptKey = message.translatedTranscriptObjectKey() == null
                ? message.rawTranscriptObjectKey()
                : message.translatedTranscriptObjectKey();
        List<RawTranscriptSegment> rawSegments = readTranscript(displayTranscriptKey);
        if (message.segmentCount() != null && message.segmentCount() != rawSegments.size()) {
            throw new IllegalArgumentException("segmentCount không khớp transcript trên MinIO");
        }
        validateSegments(rawSegments);

        transcriptRepository.deleteBySummaryId(job.getId());
        transcriptRepository.saveAll(IntStream.range(0, rawSegments.size())
                .mapToObj(index -> toDocument(job.getId(), index, rawSegments.get(index)))
                .toList());

        YoutubeSummaryContentDocument content = contentRepository.findBySummaryId(job.getId())
                .orElseGet(() -> YoutubeSummaryContentDocument.builder().summaryId(job.getId()).build());
        content.setSourceLanguage(message.detectedLanguage());
        content.setTargetLanguage(message.language());
        content.setSummary(message.summary());
        content.setKeyMoments(toKeyMoments(message.keyMoments()));
        content.setRawTranscriptObjectKey(message.rawTranscriptObjectKey());
        content.setTranslatedTranscriptObjectKey(message.translatedTranscriptObjectKey());
        content.setCaptionObjectKey(message.captionObjectKey());
        content.setSummaryObjectKey(message.summaryObjectKey());
        content.setModel(message.model());
        content.setGeneratedAt(message.completedAt() == null ? Instant.now() : message.completedAt());
        contentRepository.save(content);

        job.setStatus(YoutubeSummaryStatus.COMPLETED);
        job.setLastError(null);
        job.setCompletedAt(message.completedAt() == null ? Instant.now() : message.completedAt());
    }

    @Override
    @Transactional
    public void handleFailed(YoutubeSummaryFailedMessage message) {
        YoutubeSummaryEntity job = findJob(message.jobId());
        if (job.getStatus() == YoutubeSummaryStatus.COMPLETED) return;
        job.setStatus(YoutubeSummaryStatus.FAILED);
        String error = message.errorCode() == null ? message.errorMessage()
                : message.errorCode() + ": " + message.errorMessage();
        job.setLastError(truncate(error));
        job.setCompletedAt(message.failedAt() == null ? Instant.now() : message.failedAt());
    }

    private YoutubeSummaryEntity findJob(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Kết quả AI thiếu jobId");
        return summaryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy YouTube summary job"));
    }

    private List<RawTranscriptSegment> readTranscript(String objectKey) throws Exception {
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioBucket)
                .object(objectKey)
                .build())) {
            byte[] bytes = input.readNBytes(maxTranscriptBytes + 1);
            if (bytes.length > maxTranscriptBytes) {
                throw new IllegalArgumentException("Transcript YouTube vượt quá giới hạn cho phép");
            }
            JsonNode root = objectMapper.readTree(bytes);
            JsonNode segments = root.isArray() ? root : root.path("segments");
            if (!segments.isArray()) throw new IllegalArgumentException("Artifact transcript thiếu mảng segments");
            return objectMapper.convertValue(segments, new TypeReference<>() { });
        }
    }

    private void validateSegments(List<RawTranscriptSegment> segments) {
        if (segments.isEmpty()) throw new IllegalArgumentException("Transcript YouTube rỗng");
        double previousStart = -1;
        for (RawTranscriptSegment segment : segments) {
            if (segment.start() == null || segment.end() == null || segment.start() < 0
                    || segment.end() < segment.start() || segment.start() < previousStart
                    || segment.text() == null || segment.text().isBlank()) {
                throw new IllegalArgumentException("Transcript YouTube chứa segment không hợp lệ");
            }
            previousStart = segment.start();
        }
    }

    private YoutubeTranscriptSegmentDocument toDocument(String summaryId, int index, RawTranscriptSegment segment) {
        return YoutubeTranscriptSegmentDocument.builder()
                .summaryId(summaryId)
                .sequence((long) index)
                .startMs(Math.round(segment.start() * 1000))
                .endMs(Math.round(segment.end() * 1000))
                .text(segment.text())
                .build();
    }

    private List<YoutubeSummaryContentDocument.KeyMoment> toKeyMoments(
            List<YoutubeSummaryCompletedMessage.KeyMoment> moments) {
        if (moments == null) return List.of();
        return moments.stream()
                .filter(item -> item.startMs() != null && item.topic() != null && !item.topic().isBlank())
                .map(item -> YoutubeSummaryContentDocument.KeyMoment.builder()
                        .startMs(item.startMs())
                        .endMs(item.endMs())
                        .topic(item.topic())
                        .build())
                .toList();
    }

    private void requirePath(String expected, String actual, String name) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalArgumentException(name + " không thuộc YouTube summary hiện tại");
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "AI worker báo xử lý YouTube thất bại";
        return value.substring(0, Math.min(value.length(), 2000));
    }
}
