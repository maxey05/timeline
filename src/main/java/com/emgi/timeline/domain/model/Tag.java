package com.emgi.timeline.domain.model;

import java.util.Locale;
import java.util.Objects;

public record Tag(String name) {

    public static final int MAX_LENGTH = 32;

    public Tag {
        Objects.requireNonNull(name, "name");
        String canonical = canonicalize(name);
        if (!name.equals(canonical)) {
            throw new IllegalArgumentException(
                    "Tag name is not canonical: '" + name + "' — use Tag.of(String) instead");
        }
        requireValid(canonical);
    }

    public static Tag of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new Tag(canonicalize(raw));
    }

    private static String canonicalize(String raw) {
        return raw.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static void requireValid(String canonical) {
        if (canonical.isEmpty()) {
            throw new IllegalArgumentException("Tag name must not be blank");
        }
        if (canonical.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Tag name must be at most " + MAX_LENGTH + " characters, was " + canonical.length());
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
