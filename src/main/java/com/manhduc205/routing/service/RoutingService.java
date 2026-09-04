package com.manhduc205.routing.service;

/**
 * Routes TaskFlow AI commands to the configured message broker.
 */
public interface RoutingService {
    void publishTranscriptRequest(String messageId, String payload) throws Exception;
}
