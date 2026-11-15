package com.example.ai.messaging.publisher;

import com.example.ai.config.MessagingProperties;
import com.example.ai.dto.QueueResponse;
import com.example.ai.messaging.MessagingHeaders;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class ResponsePublisher {

    private static final Logger log = LoggerFactory.getLogger(ResponsePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    public ResponsePublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(String responseQueue, QueueResponse response) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setCorrelationId(response.correlationId());
        messageProperties.setHeader(MessagingHeaders.ACTION, response.action());
        messageProperties.setContentType(response.contentType());
        response.headers().forEach(messageProperties::setHeader);

        CorrelationData correlationData = new CorrelationData();
        try {
            rabbitTemplate.send("", responseQueue, new Message(response.body(), messageProperties), correlationData);
            CorrelationData.Confirm confirm = correlationData
                    .getFuture()
                    .get(properties.publishConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack() || correlationData.getReturned() != null) {
                throw new AmqpRejectAndDontRequeueException("Broker did not accept the queue response");
            }
            log.info(
                    "Broker accepted response. action={}, correlationId={}, responseQueue={}",
                    response.action(),
                    response.correlationId(),
                    responseQueue);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpRejectAndDontRequeueException("Interrupted while publishing queue response", exception);
        } catch (ExecutionException | TimeoutException | org.springframework.amqp.AmqpException exception) {
            throw new AmqpRejectAndDontRequeueException("Could not publish queue response", exception);
        }
    }
}
