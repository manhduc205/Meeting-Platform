package com.manhduc205.meetingplatform.configurations;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.websocket.endpoint}")
    private String endpoint;

    @Value("${app.websocket.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.websocket.broker-topic-prefix}")
    private String topicPrefix;

    @Value("${app.websocket.broker-queue-prefix}")
    private String queuePrefix;

    @Value("${app.websocket.app-destination-prefix}")
    private String appPrefix;

    private final WebSocketAuthInterceptor authInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Sử dụng biến từ file cấu hình để đăng ký Endpoint
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns(allowedOrigins.split(",")) // Hỗ trợ nhiều domain cách nhau bởi dấu phẩy
                .withSockJS();
    }
    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); // Chỉ cần 1 thread để quản lý heartbeat là đủ
        scheduler.setThreadNamePrefix("ws-heartbeat-thread-");
        scheduler.initialize();
        return scheduler;
    }
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Định tuyến tin nhắn dựa trên cấu hình môi trường
        registry.enableSimpleBroker(topicPrefix, queuePrefix)
                .setTaskScheduler(heartbeatScheduler())
                .setHeartbeatValue(new long[]{0, 30000});

        registry.setApplicationDestinationPrefixes(appPrefix);
    }

}