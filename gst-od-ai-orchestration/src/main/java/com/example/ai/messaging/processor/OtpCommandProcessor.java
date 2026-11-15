package com.example.ai.messaging.processor;

import com.example.ai.client.BrainClient;
import com.example.ai.config.MessagingProperties;
import com.example.ai.dto.BrainServiceResponse;
import com.example.ai.dto.GenerateOtpCommand;
import com.example.ai.dto.QueueCommand;
import com.example.ai.dto.QueueResponse;
import com.example.ai.dto.VerifyOtpCommand;
import com.example.ai.messaging.CommandPayloadValidator;
import com.example.ai.messaging.MessagingActions;
import com.example.ai.messaging.MessagingHeaders;
import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OtpCommandProcessor {

    private final BrainClient brainClient;
    private final ObjectMapper objectMapper;
    private final CommandPayloadValidator payloadValidator;
    private final URI otpUrl;

    public OtpCommandProcessor(
            BrainClient brainClient,
            ObjectMapper objectMapper,
            CommandPayloadValidator payloadValidator,
            MessagingProperties messagingProperties) {
        this.brainClient = brainClient;
        this.objectMapper = objectMapper;
        this.payloadValidator = payloadValidator;
        this.otpUrl = messagingProperties.brain().otpUrl();
    }

    public QueueResponse process(QueueCommand command) {
        try {
            BrainServiceResponse brainResponse = switch (command.action()) {
                case MessagingActions.GENERATE_OTP ->
                    callBrain(command, "generateotp", read(command.payload(), GenerateOtpCommand.class));
                case MessagingActions.VERIFY_OTP ->
                    callBrain(command, "verifyotp", read(command.payload(), VerifyOtpCommand.class));
                default -> throw new IllegalArgumentException("Unsupported OTP action: " + command.action());
            };
            return new QueueResponse(
                    command.action(),
                    command.correlationId(),
                    brainResponse.body(),
                    brainResponse.contentType(),
                    Map.of(MessagingHeaders.BRAIN_HTTP_STATUS, brainResponse.httpStatus()));
        } catch (ResourceAccessException exception) {
            return QueueResponse.failure(command, 502, "Brain service is unavailable");
        }
    }

    private BrainServiceResponse callBrain(QueueCommand command, String brainAction, Object payload) {
        payloadValidator.validate(payload);
        return brainClient.post(otpUrl, brainAction, payload, command.correlationId());
    }

    private <T> T read(byte[] payload, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payload, payloadType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid " + payloadType.getSimpleName() + " JSON payload", exception);
        }
    }
}
