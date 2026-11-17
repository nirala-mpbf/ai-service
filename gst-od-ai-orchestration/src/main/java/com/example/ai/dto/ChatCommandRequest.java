package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatCommandRequest(
        @NotBlank(message = "userId is required") String userId,
        @NotBlank(message = "sessionId is required") String sessionId,
        @NotBlank(message = "referenceId is required") String referenceId,
        @NotBlank(message = "message is required") String message) {}
