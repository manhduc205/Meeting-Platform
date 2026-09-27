package com.manhduc205.AI_application.dto.response;

import java.time.Instant;

public record ArtifactUrlResponse(String type, String url, Instant expiresAt) {
}
