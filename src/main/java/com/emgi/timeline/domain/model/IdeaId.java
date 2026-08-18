package com.emgi.timeline.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of an {@link Idea}.
 *
 * <p>A wrapper around {@link UUID} rather than a bare {@code UUID} or {@code long}: it costs one
 * file and makes it a compile error to pass some other kind of id where an idea's id belongs.
 *
 * <p>Ids are assigned by the application, never by the database, so both repository
 * implementations behave identically (ARCHITECTURE.md §4.2).
 */
public record IdeaId(UUID value) {

    public IdeaId {
        Objects.requireNonNull(value, "value");
    }

    /** A fresh random id. Callers inside the app should go through {@code IdGenerator} instead. */
    public static IdeaId newId() {
        return new IdeaId(UUID.randomUUID());
    }

    /**
     * Parses the canonical text form produced by {@link #toString()}.
     *
     * @throws IllegalArgumentException if the text is not a valid UUID — a malformed id is a
     *         corrupt-data or programming error, never something a user should see as a form error.
     */
    public static IdeaId fromString(String text) {
        Objects.requireNonNull(text, "text");
        return new IdeaId(UUID.fromString(text));
    }

    /** The exact form stored in SQLite's {@code idea.id TEXT} column (Phase 2). */
    @Override
    public String toString() {
        return value.toString();
    }
}
