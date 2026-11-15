package com.example.ai.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        boolean enabled,
        String otpCommandQueue,
        String otpResponseQueue,
        String chatCommandQueue,
        String chatResponseQueue,
        Duration publishConfirmTimeout,
        Duration chatResponseTimeout,
        BrainProperties brain) {

    public record BrainProperties(URI otpUrl, Duration connectTimeout, Duration readTimeout) {}
}
