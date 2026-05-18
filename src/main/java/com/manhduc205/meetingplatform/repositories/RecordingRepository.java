package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.RecordingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecordingRepository extends JpaRepository<RecordingEntity, Long> {
    Optional<RecordingEntity> findByEgressId(String egressId);
    List<RecordingEntity> findAllByMeetingCodeOrderByCreatedAtDesc(String meetingCode);
}