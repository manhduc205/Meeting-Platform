package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.enums.ParticipantRole;
import com.manhduc205.meetingplatform.models.MeetingParticipantEntity;
import com.manhduc205.meetingplatform.repositories.MeetingParticipantRepository;
import com.manhduc205.meetingplatform.services.MeetingParticipantJoinRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingParticipantJoinRecorderImpl implements MeetingParticipantJoinRecorder {

    private final MeetingParticipantRepository participantRepository;

    @Override
    @Async
    public void recordParticipantJoinAsync(String meetingId, String userId, ParticipantRole role) {
        try {
            if (!participantRepository.existsByMeetingIdAndUserId(meetingId, userId)) {
                MeetingParticipantEntity entity = MeetingParticipantEntity.builder()
                        .meetingId(meetingId)
                        .userId(userId)
                        .role(role)
                        .joinedOnceAt(LocalDateTime.now())
                        .build();

                participantRepository.save(entity);
                log.info("Đã ghi nhận điểm danh thành công cho User [{}]", userId);
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("User [{}] đã được điểm danh trước đó, bỏ qua.", userId);
        } catch (Exception e) {
            log.error("Lỗi điểm danh ngầm: {}", e.getMessage());
        }
    }
}