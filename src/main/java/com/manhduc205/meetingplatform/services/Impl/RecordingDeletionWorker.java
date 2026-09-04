package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.AI_application.repository.RecordingAiContentMongoRepository;
import com.manhduc205.AI_application.repository.RecordingTranscriptSegmentMongoRepository;
import com.manhduc205.meetingplatform.repositories.RecordingRepository;
import com.manhduc205.meetingplatform.services.RecordingDeletionService;
import com.manhduc205.meetingplatform.utils.RecordingStoragePaths;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordingDeletionWorker {
    private final RecordingRepository recordingRepository;
    private final RecordingDeletionService deletionService;
    private final RecordingAiContentMongoRepository aiContentRepository;
    private final RecordingTranscriptSegmentMongoRepository transcriptRepository;
    private final MinioClient minioClient;

    @Value("${app.minio.bucket}")
    private String minioBucket;

    @Value("${app.recording-deletion.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.recording-deletion.fixed-delay-ms:60000}")
    public void processDeletionQueue() {
        if (!enabled) return;
        Instant now = Instant.now();
        List<Long> recordingIds = recordingRepository.findPurgeableIds(now);
        for (Long recordingId : recordingIds) {
            RecordingDeletionService.PurgeTarget target = deletionService.getPurgeTarget(recordingId, now);
            if (target != null) purge(target, now);
        }
    }

    private void purge(RecordingDeletionService.PurgeTarget target, Instant now) {
        try {
            deleteMinioFiles(target);
            transcriptRepository.deleteByRecordingId(target.recordingId());
            aiContentRepository.deleteByRecordingId(target.recordingId());
            deletionService.hardDelete(target, now);
            log.info("Đã xóa vật lý recording {}", target.recordingId());
        } catch (Exception exception) {
            log.warn("Xóa vật lý recording {} thất bại, worker sẽ thử lại: {}",
                    target.recordingId(), exception.getMessage());
        }
    }

    private void deleteMinioFiles(RecordingDeletionService.PurgeTarget target) throws Exception {
        if (RecordingStoragePaths.isSafeRecordingPrefix(target.storagePrefix())) {
            deleteMinioPrefix(target.storagePrefix());
            return;
        }

        String videoObject = getLegacyObjectName(target.fileUrl());
        if (videoObject == null) {
            throw new IllegalStateException("Không tìm thấy object MinIO an toàn để xóa");
        }
        deleteObject(videoObject);

        String thumbnailObject = getLegacyObjectName(target.thumbnailUrl());
        if (thumbnailObject != null && !thumbnailObject.equals(videoObject)) {
            deleteObject(thumbnailObject);
        }
    }

    private void deleteMinioPrefix(String storagePrefix) throws Exception {
        if (!RecordingStoragePaths.isSafeRecordingPrefix(storagePrefix)) {
            throw new IllegalStateException("Storage prefix không hợp lệ, từ chối xóa: " + storagePrefix);
        }
        String objectPrefix = storagePrefix.trim() + "/";
        for (var result : minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(minioBucket)
                .prefix(objectPrefix)
                .recursive(true)
                .build())) {
            String objectName = result.get().objectName();
            if (!objectName.startsWith(objectPrefix)) {
                throw new IllegalStateException("MinIO trả về object nằm ngoài prefix cần xóa");
            }
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectName)
                    .build());
        }
    }

    private String getLegacyObjectName(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;
        try {
            String path = URI.create(fileUrl.trim()).getPath();
            String bucketPath = "/" + minioBucket + "/";
            if (path == null || !path.startsWith(bucketPath)) return null;

            String objectName = path.substring(bucketPath.length());
            if (objectName.isBlank() || objectName.endsWith("/") || objectName.contains("..")) return null;
            return objectName;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void deleteObject(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(minioBucket)
                .object(objectName)
                .build());
    }
}
