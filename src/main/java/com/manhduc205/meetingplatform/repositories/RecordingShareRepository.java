package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.RecordingShareEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordingShareRepository extends JpaRepository<RecordingShareEntity, Long> {
    void deleteByRecordingEgressId(String recordingEgressId);
}
