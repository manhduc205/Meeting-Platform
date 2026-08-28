package com.manhduc205.meetingplatform.services.Impl;

import com.manhduc205.meetingplatform.enums.OutboxEventType;
import com.manhduc205.meetingplatform.services.OutboxEventService;
import com.manhduc205.meetingplatform.services.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPollingService {
    private final OutboxEventService outboxEventService;
    private final ResendEmailService resendEmailService;

    @Value("${app.notifications.outbox.polling-enabled}")
    private boolean pollingEnabled;

    @Scheduled(fixedDelayString = "${app.notifications.outbox.fixed-delay-ms}")
    public void processOutbox() {
        if (!pollingEnabled) return;
        outboxEventService.claimPendingEvents().forEach(event -> {
            try {
                if (event.getEventType() == OutboxEventType.SEND_INVITATION_EMAIL) resendEmailService.sendInvitation(event);
                outboxEventService.markCompleted(event.getId());
            } catch (Exception exception) {
                log.warn("Không gửi được outbox event {}: {}", event.getId(), exception.getMessage());
                outboxEventService.markFailed(event.getId(), exception);
            }
        });
    }
}
