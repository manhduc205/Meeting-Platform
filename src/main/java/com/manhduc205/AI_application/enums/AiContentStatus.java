package com.manhduc205.AI_application.enums;

/**
 * Lifecycle of AI-generated material. A recording remains NOT_REQUESTED until
 * a future transcription service is explicitly requested by the user.
 */
public enum AiContentStatus {
    NOT_REQUESTED,
    REQUESTED,
    PROCESSING,
    READY,
    FAILED
}
