package com.emgi.timeline.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A label attached to an idea. A value object: in V1 a tag <em>is</em> its name, so two ideas
 * tagged {@code java} share an equal {@code Tag} and {@code Set<Tag>} deduplication is free
 * (ARCHITECTURE.md §4.3).
 *
 * <p><strong>Invariant:</strong> the {@code name} of a constructed {@code Tag} is always canonical
 * — trimmed, internal whitespace collapsed to single spaces, lowercased. The canonical form is what
 * makes {@code "Java"}, {@code " java "} and {@code "Ja  va"} behave as the user expects when
 * filtering. Because the invariant is enforced in the constructor, no code downstream (including
 * the Phase 2 row mapper) can introduce a non-canonical tag.
 *
 * <p>Prefer {@link #of(String)} for anything that came from a user or a database; the canonical
 * constructor is for code that already holds a normalized string.
 */
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

    /** Normalizes {@code raw} and returns the corresponding tag. */
    public static Tag of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new Tag(canonicalize(raw));
    }

    /**
     * trim → collapse internal whitespace runs → lowercase.
     *
     * <p>{@link Locale#ROOT} is deliberate: {@code toLowerCase()} with a Turkish default locale maps
     * {@code 'I'} to a dotless {@code 'ı'}, so the same tag would normalize differently depending on
     * the machine's locale, and tag matching would break for anyone with such a locale.
     *
     * <p>Idempotent: {@code canonicalize(canonicalize(s)).equals(canonicalize(s))}, which is what
     * lets the compact constructor use it as an invariant check.
     */
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
