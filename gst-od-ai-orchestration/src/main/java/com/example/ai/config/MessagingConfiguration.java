package com.example.ai.config;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class MessagingConfiguration {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    Queue otpCommandQueue(MessagingProperties properties) {
        return new Queue(properties.otpCommandQueue(), true);
    }

    @Bean
    Queue otpResponseQueue(MessagingProperties properties) {
        return new Queue(properties.otpResponseQueue(), true);
    }

    @Bean
    Queue chatCommandQueue(MessagingProperties properties) {
        return new Queue(properties.chatCommandQueue(), true);
    }

    @Bean
    Queue chatResponseQueue(MessagingProperties properties) {
        return new Queue(properties.chatResponseQueue(), true);
    }
}
