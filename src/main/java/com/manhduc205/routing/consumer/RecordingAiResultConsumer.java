package com.manhduc205.routing.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manhduc205.routing.configuration.RecordingRabbitTopology;
import com.manhduc205.routing.dto.TranscriptCompletedMessage;
import com.manhduc205.routing.dto.TranscriptFailedMessage;
import com.manhduc205.AI_application.service.RecordingAiResultService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecordingAiResultConsumer {
    private final ObjectMapper objectMapper;
    private final RecordingAiResultService resultService;

    @RabbitListener(queues = RecordingRabbitTopology.RESULT_QUEUE)
    public void consume(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            JsonNode root = objectMapper.readTree(message.getBody());
            String eventType = root.path("eventType").asText();
            if (RecordingRabbitTopology.COMPLETED_ROUTING_KEY.equals(eventType)) {
                resultService.handleCompleted(objectMapper.treeToValue(root, TranscriptCompletedMessage.class));
            } else if (RecordingRabbitTopology.FAILED_ROUTING_KEY.equals(eventType)) {
                resultService.handleFailed(objectMapper.treeToValue(root, TranscriptFailedMessage.class));
            } else {
                throw new IllegalArgumentException("eventType recording AI không được hỗ trợ: " + eventType);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            log.warn("Xử lý recording AI result thất bại, message sẽ vào retry: {}", exception.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
