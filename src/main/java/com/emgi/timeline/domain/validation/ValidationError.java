package com.emgi.timeline.domain.validation;

import java.util.Objects;

/**
 * One thing wrong with a command.
 *
 * @param field   a stable key such as {@code "title"} — Phase 4 attaches the message to the right
 *                control by matching this, never by parsing the message text
 * @param message a complete, user-facing sentence
 */
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
