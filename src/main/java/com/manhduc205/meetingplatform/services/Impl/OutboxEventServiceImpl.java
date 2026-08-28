package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.enums.OutboxEventStatus;
import com.manhduc205.meetingplatform.models.OutboxEventEntity;
import com.manhduc205.meetingplatform.repositories.OutboxEventRepository;
import com.manhduc205.meetingplatform.services.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxEventServiceImpl implements OutboxEventService {
    private final OutboxEventRepository outboxEventRepository;

    @Override
    @Transactional
    public List<OutboxEventEntity> claimPendingEvents() {
        Instant now = Instant.now();
        List<OutboxEventEntity> events = outboxEventRepository.lockClaimableEvents(now);
        events.forEach(event -> {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setLockedUntil(now.plus(5, ChronoUnit.MINUTES));
        });
        return events;
    }

    @Override
    @Transactional
    public void markCompleted(String eventId) {
        OutboxEventEntity event = findEvent(eventId);
        event.setStatus(OutboxEventStatus.COMPLETED);
        event.setCompletedAt(Instant.now());
        event.setLockedUntil(null);
        event.setLastError(null);
        // Invitation passcodes are present only while an event needs retrying.
        // Erase the payload after delivery instead of retaining secrets in the outbox.
        event.setPayload("{}");
    }

    @Override
    @Transactional
    public void markFailed(String eventId, Exception exception) {
        OutboxEventEntity event = findEvent(eventId);
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);
        event.setStatus(OutboxEventStatus.FAILED);
        event.setLockedUntil(null);
        event.setNextRetryAt(Instant.now().plus(Math.min(30, 1L << Math.min(attempts, 5)), ChronoUnit.MINUTES));
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        event.setLastError(message.substring(0, Math.min(message.length(), 1000)));
    }

    private OutboxEventEntity findEvent(String eventId) {
        return outboxEventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy outbox event."));
    }
}
