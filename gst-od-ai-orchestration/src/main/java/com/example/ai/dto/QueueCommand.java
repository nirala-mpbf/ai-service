package com.example.ai.dto;

import com.example.ai.messaging.MessagingHeaders;
import java.util.Map;

/** A queue message with RabbitMQ headers and an action-specific JSON body. */
public record QueueCommand(Map<String, Object> headers, byte[] payload, String correlationId) {

    public QueueCommand {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("RabbitMQ headers are required");
        }
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Command payload is required");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ property correlation_id is required");
        }
        headers = Map.copyOf(headers);
    }

    public String action() {
        Object value = headers.get(MessagingHeaders.ACTION);
        if (!(value instanceof String action) || action.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ header Action is required");
        }
        return action;
    }
}
