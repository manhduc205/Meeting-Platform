package com.manhduc205.AI_application.repository;

import com.manhduc205.AI_application.enums.RecordingAiJobStatus;
import com.manhduc205.AI_application.entity.RecordingAiJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface RecordingAiJobRepository extends JpaRepository<RecordingAiJobEntity, String> {
    Optional<RecordingAiJobEntity> findFirstByRecordingIdAndOperationOrderByVersionDesc(Long recordingId, String operation);

    Optional<RecordingAiJobEntity> findFirstByRecordingIdAndOperationAndStatusInOrderByVersionDesc(
            Long recordingId, String operation, Collection<RecordingAiJobStatus> statuses);

    List<RecordingAiJobEntity> findAllByRecordingId(Long recordingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from RecordingAiJobEntity job where job.recordingId = :recordingId")
    List<RecordingAiJobEntity> findAllByRecordingIdForUpdate(@Param("recordingId") Long recordingId);
}
