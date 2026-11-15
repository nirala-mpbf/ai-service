package com.example.ai.dto;

import com.example.ai.messaging.MessagingHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** A response that can be published to any RabbitMQ response queue. */
public record QueueResponse(
        String action, String correlationId, byte[] body, String contentType, Map<String, Object> headers) {

    public static QueueResponse failure(QueueCommand command, int status, String error) {
        return new QueueResponse(
                command.action(),
                command.correlationId(),
                ("{\"error\":\"" + error + "\"}").getBytes(StandardCharsets.UTF_8),
                "application/json",
                Map.of(MessagingHeaders.PROCESSING_STATUS, status));
    }
}
