package com.emgi.timeline.domain.validation;

import java.util.Objects;

public record ValidationError(String field, String message) {

    public ValidationError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
        if (field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
