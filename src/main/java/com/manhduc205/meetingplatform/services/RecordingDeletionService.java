package com.manhduc205.meetingplatform.services;

import com.manhduc205.AI_application.entity.RecordingAiJobEntity;
import com.manhduc205.AI_application.enums.RecordingAiJobStatus;
import com.manhduc205.AI_application.repository.RecordingAiJobRepository;
import com.manhduc205.meetingplatform.enums.RecordingStatus;
import com.manhduc205.meetingplatform.models.RecordingEntity;
import com.manhduc205.meetingplatform.repositories.MeetingRepository;
import com.manhduc205.meetingplatform.repositories.OutboxEventRepository;
import com.manhduc205.meetingplatform.repositories.RecordingRepository;
import com.manhduc205.meetingplatform.repositories.RecordingShareRepository;
import com.manhduc205.meetingplatform.utils.UserContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RecordingDeletionService {
    private final RecordingRepository recordingRepository;
    private final MeetingRepository meetingRepository;
    private final RecordingAiJobRepository aiJobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RecordingShareRepository recordingShareRepository;

    @Value("${app.recording-deletion.trash-retention-days:3}")
    private long trashRetentionDays;

    @Transactional
    public Instant moveToTrash(Long recordingId) {
        RecordingEntity recording = findOwnedForUpdate(recordingId);
        Instant now = Instant.now();
        if (recording.getPurgeAfter() != null) {
            if (recording.getPurgeAfter().isAfter(now)) return recording.getPurgeAfter();
            throw new IllegalStateException("Bản ghi đang chờ xóa vĩnh viễn");
        }

        requireFinished(recording);
        removeQueuedAiWork(recordingId);
        Instant purgeAfter = now.plus(trashRetentionDays, ChronoUnit.DAYS);
        recording.setPurgeAfter(purgeAfter);
        return purgeAfter;
    }

    @Transactional
    public void restore(Long recordingId) {
        RecordingEntity recording = findOwnedForUpdate(recordingId);
        Instant purgeAfter = recording.getPurgeAfter();
        if (purgeAfter == null) {
            throw new IllegalStateException("Bản ghi không nằm trong thùng rác");
        }
        if (!purgeAfter.isAfter(Instant.now())) {
            throw new IllegalStateException("Bản ghi đã hết thời gian khôi phục");
        }
        recording.setPurgeAfter(null);
    }

    @Transactional
    public Instant requestPermanentDeletion(Long recordingId) {
        RecordingEntity recording = findOwnedForUpdate(recordingId);
        requireFinished(recording);
        removeQueuedAiWork(recordingId);
        Instant purgeAfter = Instant.now();
        recording.setPurgeAfter(purgeAfter);
        return purgeAfter;
    }

    @Transactional(readOnly = true)
    public PurgeTarget getPurgeTarget(Long recordingId, Instant now) {
        RecordingEntity recording = recordingRepository.findById(recordingId).orElse(null);
        if (recording == null || recording.getPurgeAfter() == null
                || recording.getPurgeAfter().isAfter(now)) {
            return null;
        }
        return new PurgeTarget(
                recording.getId(),
                recording.getEgressId(),
                recording.getStoragePrefix(),
                recording.getFileUrl(),
                recording.getThumbnailUrl());
    }

    @Transactional
    public void hardDelete(PurgeTarget target, Instant now) {
        RecordingEntity recording = recordingRepository.findByIdForUpdate(target.recordingId()).orElse(null);
        if (recording == null || recording.getPurgeAfter() == null
                || recording.getPurgeAfter().isAfter(now)
                || !Objects.equals(recording.getStoragePrefix(), target.storagePrefix())
                || !Objects.equals(recording.getFileUrl(), target.fileUrl())
                || !Objects.equals(recording.getThumbnailUrl(), target.thumbnailUrl())) {
            return;
        }

        List<RecordingAiJobEntity> jobs = aiJobRepository.findAllByRecordingId(recording.getId());
        List<String> jobIds = new ArrayList<>();
        for (RecordingAiJobEntity job : jobs) {
            jobIds.add(job.getId());
        }
        if (!jobIds.isEmpty()) {
            outboxEventRepository.deleteAllByAggregateIdIn(jobIds);
        }

        recordingShareRepository.deleteByRecordingEgressId(recording.getEgressId());
        recordingRepository.delete(recording);
    }

    private RecordingEntity findOwnedForUpdate(Long recordingId) {
        RecordingEntity recording = recordingRepository.findByIdForUpdate(recordingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi"));
        String hostId = meetingRepository.findByMeetingCode(recording.getMeetingCode())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cuộc họp của bản ghi"))
                .getHostId();
        if (!hostId.equals(UserContext.getUserId())) {
            throw new SecurityException("Chỉ chủ phòng mới có quyền xóa hoặc khôi phục bản ghi");
        }
        return recording;
    }

    private void requireFinished(RecordingEntity recording) {
        if (recording.getStatus() == RecordingStatus.STARTING
                || recording.getStatus() == RecordingStatus.RECORDING) {
            throw new IllegalStateException("Hãy dừng ghi hình trước khi xóa bản ghi");
        }
    }

    private void removeQueuedAiWork(Long recordingId) {
        List<RecordingAiJobEntity> jobs = aiJobRepository.findAllByRecordingIdForUpdate(recordingId);
        for (RecordingAiJobEntity job : jobs) {
            if (job.getStatus() == RecordingAiJobStatus.PUBLISHED) {
                throw new IllegalStateException("Bản ghi đang được AI xử lý, vui lòng thử lại sau");
            }
        }

        List<String> queuedJobIds = new ArrayList<>();
        List<RecordingAiJobEntity> queuedJobs = new ArrayList<>();
        for (RecordingAiJobEntity job : jobs) {
            if (job.getStatus() == RecordingAiJobStatus.REQUESTED) {
                queuedJobIds.add(job.getId());
                queuedJobs.add(job);
            }
        }
        if (!queuedJobIds.isEmpty()) {
            outboxEventRepository.deleteAllByAggregateIdIn(queuedJobIds);
            aiJobRepository.deleteAll(queuedJobs);
        }
    }

    public record PurgeTarget(
            Long recordingId,
            String egressId,
            String storagePrefix,
            String fileUrl,
            String thumbnailUrl) {
    }
}
