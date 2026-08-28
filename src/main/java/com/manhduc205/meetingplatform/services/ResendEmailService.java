package com.manhduc205.meetingplatform.services;

import com.manhduc205.meetingplatform.models.OutboxEventEntity;

public interface ResendEmailService {
    void sendInvitation(OutboxEventEntity event);
}
