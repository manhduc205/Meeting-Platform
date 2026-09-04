package com.manhduc205.AI_application.dto.request;

import jakarta.validation.constraints.Pattern;

public record TranscriptRequestRequest(
        @Pattern(regexp = "^vi$", message = "Worker hiện chỉ hỗ trợ transcript tiếng Việt (vi)")
        String language
) {
}
