package com.manhduc205.routing.service.impl;

import com.manhduc205.routing.configuration.RecordingRabbitTopology;
import com.manhduc205.routing.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RoutingServiceImpl implements RoutingService {
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.recording-ai.publisher-confirm-timeout-seconds:10}")
    private long confirmTimeoutSeconds;

    @Override
    public void publishTranscriptRequest(String messageId, String payload) throws Exception {
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(messageId)
                .build();
        CorrelationData correlation = new CorrelationData(messageId);
        rabbitTemplate.send(
                RecordingRabbitTopology.COMMAND_EXCHANGE,
                RecordingRabbitTopology.REQUEST_ROUTING_KEY,
                message,
                correlation
        );
        CorrelationData.Confirm confirm = correlation.getFuture()
                .get(confirmTimeoutSeconds, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ không xác nhận message: " + confirm.getReason());
        }
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new IllegalStateException("RabbitMQ không route được message tới transcript queue");
        }
    }
}
