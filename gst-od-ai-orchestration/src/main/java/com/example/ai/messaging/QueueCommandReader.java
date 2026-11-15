package com.example.ai.messaging;

import com.example.ai.dto.QueueCommand;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
public class QueueCommandReader {

    public QueueCommand read(Message message) {
        try {
            return new QueueCommand(
                    message.getMessageProperties().getHeaders(),
                    message.getBody(),
                    message.getMessageProperties().getCorrelationId());
        } catch (IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid queue command message", exception);
        }
    }
}
