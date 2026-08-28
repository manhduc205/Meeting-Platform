package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.enums.InvitationStatus;
import com.manhduc205.meetingplatform.models.MeetingInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MeetingInvitationRepository extends JpaRepository<MeetingInvitationEntity, String> {
    List<MeetingInvitationEntity> findAllByMeetingIdOrderByCreatedAtAsc(String meetingId);
    List<MeetingInvitationEntity> findAllByInviteeEmailAndStatusIn(String inviteeEmail, Collection<InvitationStatus> statuses);
    boolean existsByMeetingIdAndInviteeEmail(String meetingId, String inviteeEmail);
    boolean existsByMeetingIdAndInviteeEmailAndStatusIn(String meetingId, String inviteeEmail, Collection<InvitationStatus> statuses);
    boolean existsByMeetingIdAndInviteeUserIdAndStatusIn(String meetingId, String inviteeUserId, Collection<InvitationStatus> statuses);
}
