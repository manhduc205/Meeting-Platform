package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.MeetingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingRepository extends JpaRepository<MeetingEntity, String> {
    Optional<MeetingEntity> findByMeetingCode(String meetingCode);

    List<MeetingEntity> findAllByHostIdOrderByCreatedAtDesc(String hostId);

    boolean existsByMeetingCode(String meetingCode);
}
