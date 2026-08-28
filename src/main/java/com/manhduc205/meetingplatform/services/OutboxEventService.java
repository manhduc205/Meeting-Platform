package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.OutboxEventEntity;

import java.util.List;

public interface OutboxEventService {
    List<OutboxEventEntity> claimPendingEvents();
    void markCompleted(String eventId);
    void markFailed(String eventId, Exception exception);
}
