package com.manhduc205.AI_application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecordingChatRequest(
        @NotBlank @Size(max = 2000) String query,
        List<Message> conversationHistory
) {
    public record Message(String role, @Size(max = 4000) String text) {
    }
}
