package com.example.ai.messaging.processor;

import com.example.ai.config.MessagingProperties;
import com.example.ai.dto.ChatCommandRequest;
import com.example.ai.dto.ChatCommandResponse;
import com.example.ai.dto.QueueCommand;
import com.example.ai.dto.QueueResponse;
import com.example.ai.messaging.CommandPayloadValidator;
import com.example.ai.messaging.MessagingActions;
import com.example.ai.service.ChatService;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ChatCommandProcessor {

    private final ObjectMapper objectMapper;
    private final CommandPayloadValidator payloadValidator;
    private final ChatService chatService;
    private final MessagingProperties properties;

    public ChatCommandProcessor(
            ObjectMapper objectMapper,
            CommandPayloadValidator payloadValidator,
            ChatService chatService,
            MessagingProperties properties) {
        this.objectMapper = objectMapper;
        this.payloadValidator = payloadValidator;
        this.chatService = chatService;
        this.properties = properties;
    }

    public QueueResponse process(QueueCommand command) {
        if (!MessagingActions.NORMAL_CHAT.equals(command.action())) {
            throw new IllegalArgumentException("Unsupported chat action: " + command.action());
        }

        ChatCommandRequest request = read(command.payload());
        payloadValidator.validate(request);
        ChatCommandResponse response = chatService.chat(request).block(properties.chatResponseTimeout());
        if (response == null) {
            throw new IllegalStateException("Chat service returned no response");
        }

        return new QueueResponse(
                command.action(), command.correlationId(), write(response), "application/json", Map.of());
    }

    private ChatCommandRequest read(byte[] payload) {
        try {
            return objectMapper.readValue(payload, ChatCommandRequest.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid ChatCommandRequest JSON payload", exception);
        }
    }

    private byte[] write(ChatCommandResponse response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize chat response", exception);
        }
    }
}
