package com.manhduc205.routing.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecordingRabbitTopology {
    public static final String COMMAND_EXCHANGE = "recording.ai.command";
    public static final String EVENT_EXCHANGE = "recording.ai.event";
    public static final String DEAD_LETTER_EXCHANGE = "recording.ai.dlx";
    public static final String RETRY_EXCHANGE = "recording.ai.retry";

    public static final String REQUEST_QUEUE = "recording.ai.transcript.request.v1";
    public static final String RESULT_QUEUE = "recording.ai.transcript.result.v1";
    public static final String DEAD_LETTER_QUEUE = "recording.ai.dead-letter.v1";
    public static final String FINAL_DLQ = "recording.ai.final.dlq.v1";

    public static final String REQUEST_ROUTING_KEY = "transcript.requested";
    public static final String COMPLETED_ROUTING_KEY = "transcript.completed";
    public static final String FAILED_ROUTING_KEY = "transcript.failed";
    public static final String REQUEST_DEAD_KEY = "request.dead";
    public static final String RESULT_DEAD_KEY = "result.dead";

    public static final String REQUEST_RETRY_10S = "recording.ai.request.retry.10s.v1";
    public static final String REQUEST_RETRY_60S = "recording.ai.request.retry.60s.v1";
    public static final String REQUEST_RETRY_300S = "recording.ai.request.retry.300s.v1";
    public static final String RESULT_RETRY_10S = "recording.ai.result.retry.10s.v1";
    public static final String RESULT_RETRY_60S = "recording.ai.result.retry.60s.v1";
    public static final String RESULT_RETRY_300S = "recording.ai.result.retry.300s.v1";

    @Bean
    TopicExchange recordingCommandExchange() {
        return new TopicExchange(COMMAND_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange recordingEventExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange recordingDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange recordingRetryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    Queue recordingRequestQueue() {
        return mainQueue(REQUEST_QUEUE, REQUEST_DEAD_KEY);
    }

    @Bean
    Queue recordingResultQueue() {
        return mainQueue(RESULT_QUEUE, RESULT_DEAD_KEY);
    }

    @Bean
    Queue recordingDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Queue recordingFinalDeadLetterQueue() {
        return QueueBuilder.durable(FINAL_DLQ).build();
    }

    @Bean
    Binding recordingRequestBinding(Queue recordingRequestQueue, TopicExchange recordingCommandExchange) {
        return BindingBuilder.bind(recordingRequestQueue).to(recordingCommandExchange).with(REQUEST_ROUTING_KEY);
    }

    @Bean
    Binding recordingCompletedBinding(Queue recordingResultQueue, TopicExchange recordingEventExchange) {
        return BindingBuilder.bind(recordingResultQueue).to(recordingEventExchange).with(COMPLETED_ROUTING_KEY);
    }

    @Bean
    Binding recordingFailedBinding(Queue recordingResultQueue, TopicExchange recordingEventExchange) {
        return BindingBuilder.bind(recordingResultQueue).to(recordingEventExchange).with(FAILED_ROUTING_KEY);
    }

    @Bean
    Binding recordingRequestDeadBinding(Queue recordingDeadLetterQueue, DirectExchange recordingDeadLetterExchange) {
        return BindingBuilder.bind(recordingDeadLetterQueue).to(recordingDeadLetterExchange).with(REQUEST_DEAD_KEY);
    }

    @Bean
    Binding recordingResultDeadBinding(Queue recordingDeadLetterQueue, DirectExchange recordingDeadLetterExchange) {
        return BindingBuilder.bind(recordingDeadLetterQueue).to(recordingDeadLetterExchange).with(RESULT_DEAD_KEY);
    }

    @Bean
    Queue recordingRequestRetry10s() { return retryQueue(REQUEST_RETRY_10S, 10_000, "", REQUEST_QUEUE); }
    @Bean
    Queue recordingRequestRetry60s() { return retryQueue(REQUEST_RETRY_60S, 60_000, "", REQUEST_QUEUE); }
    @Bean
    Queue recordingRequestRetry300s() { return retryQueue(REQUEST_RETRY_300S, 300_000, "", REQUEST_QUEUE); }
    @Bean
    Queue recordingResultRetry10s() { return retryQueue(RESULT_RETRY_10S, 10_000, "", RESULT_QUEUE); }
    @Bean
    Queue recordingResultRetry60s() { return retryQueue(RESULT_RETRY_60S, 60_000, "", RESULT_QUEUE); }
    @Bean
    Queue recordingResultRetry300s() { return retryQueue(RESULT_RETRY_300S, 300_000, "", RESULT_QUEUE); }

    @Bean
    Binding recordingRequestRetry10sBinding(Queue recordingRequestRetry10s, DirectExchange recordingRetryExchange) {
        return BindingBuilder.bind(recordingRequestRetry10s).to(recordingRetryExchange).with(REQUEST_RETRY_10S);
    }
    @Bean
    Binding recordingRequestRetry60sBinding(Queue recordingRequestRetry60s, DirectExchange recordingRetryExchange) {
        return BindingBuilder.bind(recordingRequestRetry60s).to(recordingRetryExchange).with(REQUEST_RETRY_60S);
    }
    @Bean
    Binding recordingRequestRetry300sBinding(Queue recordingRequestRetry300s, DirectExchange recordingRetryExchange) {
        return BindingBuilder.bind(recordingRequestRetry300s).to(recordingRetryExchange).with(REQUEST_RETRY_300S);
    }
    @Bean
    Binding recordingResultRetry10sBinding(Queue recordingResultRetry10s, DirectExchange recordingRetryExchange) {
        return BindingBuilder.bind(recordingResultRetry10s).to(recordingRetryExchange).with(RESULT_RETRY_10S);
    }
    @Bean
    Binding recordingResultRetry60sBinding(Queue recordingResultRetry60s, DirectExchange recordingRetryExchange) {
        return BindingBuilder.bind(recordingResultRetry60s).to(recordingRetryExchange).with(RESULT_RETRY_60S);
    }
    @Bean
    Binding recordingResultRetry300sBinding(Queue recordingResultRetry300s, DirectExchange recordingRetryExchange) {
        return BindingBuilder.bind(recordingResultRetry300s).to(recordingRetryExchange).with(RESULT_RETRY_300S);
    }

    @Bean
    RabbitTemplateCustomizer mandatoryRabbitMessages() {
        return template -> template.setMandatory(true);
    }

    private Queue mainQueue(String name, String deadRoutingKey) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", deadRoutingKey)
                .build();
    }

    private Queue retryQueue(String name, int ttl, String targetExchange, String targetRoutingKey) {
        return QueueBuilder.durable(name)
                .withArgument("x-message-ttl", ttl)
                .withArgument("x-dead-letter-exchange", targetExchange)
                .withArgument("x-dead-letter-routing-key", targetRoutingKey)
                .build();
    }
}
