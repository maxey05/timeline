package com.emgi.timeline.domain.validation;

import java.util.List;
import java.util.Objects;

public record ValidationResult(List<ValidationError> errors) {

    private static final ValidationResult VALID = new ValidationResult(List.of());

    public ValidationResult {
        Objects.requireNonNull(errors, "errors");
        errors = List.copyOf(errors);
    }

    public static ValidationResult valid() {
        return VALID;
    }

    public static ValidationResult of(List<ValidationError> errors) {
        return errors.isEmpty() ? VALID : new ValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean isInvalid() {
        return !isValid();
    }

    public List<String> messagesFor(String field) {
        Objects.requireNonNull(field, "field");
        return errors.stream()
                .filter(error -> error.field().equals(field))
                .map(ValidationError::message)
                .toList();
    }
}
