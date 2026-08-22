package com.manhduc205.meetingplatform.configurations;

import io.livekit.server.EgressServiceClient;
import io.livekit.server.RoomServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiveKitConfig {

    @Value("${app.livekit.host}")
    private String host;

    @Value("${app.livekit.api-key}")
    private String apiKey;

    @Value("${app.livekit.api-secret}")
    private String apiSecret;

    @Bean
    public EgressServiceClient egressServiceClient() {
        return EgressServiceClient.create(host, apiKey, apiSecret);
    }

    @Bean
    public RoomServiceClient roomServiceClient() {
        return RoomServiceClient.create(host, apiKey, apiSecret);
    }
}