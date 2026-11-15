package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenerateOtpCommand(
        @NotBlank(message = "referenceId is required")
        @Size(max = 100, message = "referenceId must not exceed 100 characters")
        String referenceId,

        @NotBlank(message = "mobileNumber is required")
        @Pattern(regexp = "[0-9]{10,15}", message = "mobileNumber must contain 10 to 15 digits")
        String mobileNumber) {}
