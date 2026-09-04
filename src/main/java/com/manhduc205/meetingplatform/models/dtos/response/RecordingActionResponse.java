package com.manhduc205.meetingplatform.models.dtos.response;

import java.time.Instant;

public record RecordingActionResponse(String message, Instant purgeAt) {
}
