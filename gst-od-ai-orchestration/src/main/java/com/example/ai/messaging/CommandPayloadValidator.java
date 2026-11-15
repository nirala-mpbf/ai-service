package com.example.ai.messaging;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CommandPayloadValidator {

    private final Validator validator;

    public CommandPayloadValidator(Validator validator) {
        this.validator = validator;
    }

    public <T> void validate(T payload) {
        Set<ConstraintViolation<T>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
    }
}
