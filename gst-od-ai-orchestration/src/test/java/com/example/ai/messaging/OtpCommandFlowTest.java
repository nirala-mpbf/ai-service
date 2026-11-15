package com.example.ai.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.example.ai.client.BrainClient;
import com.example.ai.config.MessagingProperties;
import com.example.ai.messaging.consumer.OtpCommandConsumer;
import com.example.ai.messaging.processor.OtpCommandProcessor;
import com.example.ai.messaging.publisher.ResponsePublisher;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OtpCommandFlowTest {

    @Test
    void routesOtpCommandsAndPublishesResponses() throws Exception {
        HttpServer brain = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = new CopyOnWriteArrayList<>();
        brain.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod()
                    + " "
                    + exchange.getRequestURI()
                    + " "
                    + exchange.getRequestHeaders().getFirst("X-Action")
                    + " "
                    + exchange.getRequestHeaders().getFirst("X-Correlation-Id")
                    + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean verify = "verifyotp".equals(exchange.getRequestHeaders().getFirst("X-Action"));
            byte[] body = (verify ? "{\"verified\":false}" : "{\"generated\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(verify ? 422 : 200, body.length);
            try (exchange) {
                exchange.getResponseBody().write(body);
            }
        });
        brain.start();
        try {
            String baseUrl = "http://127.0.0.1:" + brain.getAddress().getPort();
            MessagingProperties properties = properties(baseUrl);
            JsonMapper objectMapper = JsonMapper.builder().build();
            RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
            List<Message> publishedMessages = new ArrayList<>();
            doAnswer(invocation -> {
                        publishedMessages.add(invocation.getArgument(2));
                        CorrelationData correlationData = invocation.getArgument(3);
                        correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
                        return null;
                    })
                    .when(rabbitTemplate)
                    .send(eq(""), eq("meg.otp.response.queue"), any(Message.class), any(CorrelationData.class));

            ResponsePublisher publisher = new ResponsePublisher(rabbitTemplate, properties);
            CommandPayloadValidator payloadValidator = new CommandPayloadValidator(
                    Validation.buildDefaultValidatorFactory().getValidator());
            OtpCommandProcessor processor = new OtpCommandProcessor(
                    new BrainClient(RestClient.builder(), properties), objectMapper, payloadValidator, properties);
            OtpCommandConsumer consumer =
                    new OtpCommandConsumer(new QueueCommandReader(), processor, publisher, properties);
            consumer.consume(command(
                    MessagingActions.GENERATE_OTP,
                    "{\"referenceId\":\"R07\",\"mobileNumber\":\"9999999999\"}",
                    "generate-1"));
            consumer.consume(
                    command(MessagingActions.VERIFY_OTP, "{\"referenceId\":\"R07\",\"otp\":\"000000\"}", "verify-1"));

            assertThat(requests)
                    .containsExactly(
                            "POST /api/otp generateotp generate-1 {\"referenceId\":\"R07\",\"mobileNumber\":\"9999999999\"}",
                            "POST /api/otp verifyotp verify-1 {\"referenceId\":\"R07\",\"otp\":\"000000\"}");
            assertThat(publishedMessages).hasSize(2);
            assertThat(publishedMessages.get(1).getBody())
                    .asString(StandardCharsets.UTF_8)
                    .isEqualTo("{\"verified\":false}");
            assertThat(publishedMessages.get(1).getMessageProperties().getCorrelationId())
                    .isEqualTo("verify-1");
            assertThat(publishedMessages
                            .get(1)
                            .getMessageProperties()
                            .<Integer>getHeader(MessagingHeaders.BRAIN_HTTP_STATUS))
                    .isEqualTo(422);
        } finally {
            brain.stop(0);
        }
    }

    private static MessagingProperties properties(String baseUrl) {
        return new MessagingProperties(
                true,
                "meg.otp.command.queue",
                "meg.otp.response.queue",
                "meg.chat.command.queue",
                "meg.chat.response.queue",
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                new MessagingProperties.BrainProperties(
                        URI.create(baseUrl + "/api/otp"), Duration.ofSeconds(5), Duration.ofSeconds(15)));
    }

    private static Message command(String action, String payload, String correlationId) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(MessagingHeaders.ACTION, action);
        properties.setCorrelationId(correlationId);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }
}
