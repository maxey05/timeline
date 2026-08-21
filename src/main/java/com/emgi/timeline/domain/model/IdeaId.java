package com.emgi.timeline.domain.model;

import java.util.Objects;
import java.util.UUID;

public record IdeaId(UUID value) {

    public IdeaId {
        Objects.requireNonNull(value, "value");
    }

    public static IdeaId newId() {
        return new IdeaId(UUID.randomUUID());
    }

    public static IdeaId fromString(String text) {
        Objects.requireNonNull(text, "text");
        return new IdeaId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
