package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.MeetingParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipantEntity, Long> {
    boolean existsByMeetingIdAndUserId(String meetingId, String userId);

    List<MeetingParticipantEntity> findAllByMeetingIdOrderByJoinedOnceAtAsc(String meetingId);
}