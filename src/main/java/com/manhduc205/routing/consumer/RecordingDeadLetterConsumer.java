package com.manhduc205.routing.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.routing.configuration.RecordingRabbitTopology;
import com.manhduc205.AI_application.service.RecordingAiJobService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecordingDeadLetterConsumer {
    private static final String RETRY_HEADER = "recording-ai-retry-count";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RecordingAiJobService recordingAiJobService;

    @Value("${app.recording-ai.max-consumer-retries:3}")
    private int maxRetries;

    @Value("${app.recording-ai.publisher-confirm-timeout-seconds:10}")
    private long confirmTimeoutSeconds;

    @RabbitListener(queues = RecordingRabbitTopology.DEAD_LETTER_QUEUE)
    public void route(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            int retryCount = retryCount(message) + 1;
            message.getMessageProperties().setHeader(RETRY_HEADER, retryCount);
            String deadKey = message.getMessageProperties().getReceivedRoutingKey();

            if (retryCount > maxRetries) {
                sendConfirmed("", RecordingRabbitTopology.FINAL_DLQ, message);
                markJobFailed(message, retryCount - 1);
                log.error("Recording AI message {} đã vào DLQ sau {} lần thử",
                        message.getMessageProperties().getMessageId(), retryCount - 1);
            } else {
                sendConfirmed(
                        RecordingRabbitTopology.RETRY_EXCHANGE,
                        retryQueue(deadKey, retryCount),
                        message
                );
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            log.error("Không thể định tuyến recording AI dead letter", exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private String retryQueue(String deadKey, int retryCount) {
        boolean request = RecordingRabbitTopology.REQUEST_DEAD_KEY.equals(deadKey);
        if (!request && !RecordingRabbitTopology.RESULT_DEAD_KEY.equals(deadKey)) {
            throw new IllegalArgumentException("Dead-letter routing key không hợp lệ: " + deadKey);
        }
        if (request) {
            return retryCount == 1 ? RecordingRabbitTopology.REQUEST_RETRY_10S
                    : retryCount == 2 ? RecordingRabbitTopology.REQUEST_RETRY_60S
                    : RecordingRabbitTopology.REQUEST_RETRY_300S;
        }
        return retryCount == 1 ? RecordingRabbitTopology.RESULT_RETRY_10S
                : retryCount == 2 ? RecordingRabbitTopology.RESULT_RETRY_60S
                : RecordingRabbitTopology.RESULT_RETRY_300S;
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_HEADER);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void sendConfirmed(String exchange, String routingKey, Message message) throws Exception {
        CorrelationData correlation = new CorrelationData();
        rabbitTemplate.send(exchange, routingKey, message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeoutSeconds, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ không xác nhận dead-letter route");
        }
    }

    private void markJobFailed(Message message, int attempts) {
        try {
            JsonNode root = objectMapper.readTree(message.getBody());
            JsonNode jobId = root.get("jobId");
            if (jobId != null && !jobId.asText().isBlank()) {
                recordingAiJobService.markInfrastructureFailed(
                        jobId.asText(),
                        "RabbitMQ không xử lý được message sau " + attempts + " lần thử; đã chuyển vào DLQ cuối"
                );
            }
        } catch (Exception exception) {
            log.warn("Không thể cập nhật trạng thái job từ recording AI DLQ", exception);
        }
    }
}
