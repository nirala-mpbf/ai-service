package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpCommand(
        @NotBlank(message = "referenceId is required")
        @Size(max = 100, message = "referenceId must not exceed 100 characters")
        String referenceId,

        @NotBlank(message = "otp is required")
        @Pattern(regexp = "[0-9]{6}", message = "otp must contain exactly 6 digits")
        String otp) {}
