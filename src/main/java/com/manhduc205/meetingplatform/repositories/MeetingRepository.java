package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.MeetingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Repository
public interface MeetingRepository extends JpaRepository<MeetingEntity, String> {
    boolean existsByMeetingCode(String meetingCode);
    Optional<MeetingEntity> findByMeetingCode(String meetingCode);
    List<MeetingEntity> findAllByHostIdOrderByPlannedStartTimeAsc(String hostId);
    List<MeetingEntity> findAllByHostIdAndPlannedStartTimeLessThanAndPlannedEndTimeGreaterThanOrderByPlannedStartTimeAsc(
            String hostId, Instant to, Instant from);
    List<MeetingEntity> findAllByMeetingCodeIn(List<String> meetingCodes);
}
