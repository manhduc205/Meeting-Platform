package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.enums.ParticipantRole;

public interface MeetingParticipantJoinRecorder {
    void recordParticipantJoinAsync(String meetingId, String userId, ParticipantRole role);
}