package com.emgi.timeline.domain.validation;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of validating a command: every problem found, not just the first one.
 *
 * <p>Reporting all errors at once is what stops the Phase 4 form from feeling broken — a user who
 * fixes the title and is then told about the URL, one round trip at a time, concludes the app is
 * fighting them.
 */
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

    /** The messages for one field, in the order they were reported. Empty if that field is fine. */
    public List<String> messagesFor(String field) {
        Objects.requireNonNull(field, "field");
        return errors.stream()
                .filter(error -> error.field().equals(field))
                .map(ValidationError::message)
                .toList();
    }
}
