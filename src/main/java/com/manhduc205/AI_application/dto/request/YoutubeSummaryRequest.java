package com.manhduc205.AI_application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record YoutubeSummaryRequest(
        @NotBlank @Size(max = 500) String url,
        @Pattern(regexp = "^[A-Za-z]{2,3}(?:-[A-Za-z]{2})?$") String language
) {
}
