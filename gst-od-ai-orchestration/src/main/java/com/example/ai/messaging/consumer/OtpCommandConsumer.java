package com.example.ai.messaging.consumer;

import com.example.ai.config.MessagingProperties;
import com.example.ai.dto.QueueCommand;
import com.example.ai.dto.QueueResponse;
import com.example.ai.messaging.QueueCommandReader;
import com.example.ai.messaging.processor.OtpCommandProcessor;
import com.example.ai.messaging.publisher.ResponsePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class OtpCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(OtpCommandConsumer.class);

    private final QueueCommandReader commandReader;
    private final OtpCommandProcessor processor;
    private final ResponsePublisher responsePublisher;
    private final MessagingProperties properties;

    public OtpCommandConsumer(
            QueueCommandReader commandReader,
            OtpCommandProcessor processor,
            ResponsePublisher responsePublisher,
            MessagingProperties properties) {
        this.commandReader = commandReader;
        this.processor = processor;
        this.responsePublisher = responsePublisher;
        this.properties = properties;
    }

    @RabbitListener(queues = "${app.messaging.otp-command-queue}")
    public void consume(Message message) {
        QueueCommand command = commandReader.read(message);
        QueueResponse response;
        try {
            response = processor.process(command);
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "Rejected OTP command. correlationId={}, reason={}",
                    command.correlationId(),
                    exception.getMessage());
            response = QueueResponse.failure(command, 400, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Could not process OTP command. correlationId={}", command.correlationId(), exception);
            response = QueueResponse.failure(command, 500, "Could not process OTP command");
        }
        responsePublisher.publish(properties.otpResponseQueue(), response);
    }
}
