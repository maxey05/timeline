package com.emgi.timeline.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A captured idea — the central entity (ARCHITECTURE.md §4.1).
 *
 * <p>Immutable by design. Editing produces a new instance that the caller substitutes into the
 * list, rather than mutating shared state that any view could quietly change. The mutable, bindable
 * state the editor needs while the user types lives in {@code IdeaEditorController} as a form model
 * and becomes an {@code Idea} only on save.
 *
 * <p><strong>An {@code Idea} never reads the clock.</strong> {@link #withUpdatedAt(Instant)} takes
 * the timestamp as an argument because the service owns the injected {@link java.time.Clock} — that
 * is what lets service tests assert an exact instant instead of "roughly now".
 *
 * <p>There is deliberately no {@code withCreatedAt}: {@code createdAt} is set once at construction
 * and preserved by every update path (§7.2).
 */
public record Idea(
        IdeaId id,
        String title,
        Description description,
        Set<Tag> tags,
        IdeaStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public Idea {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        tags = Set.copyOf(tags); // immutable snapshot; also rejects null elements
    }

    public Idea withTitle(String newTitle) {
        return new Idea(id, newTitle, description, tags, status, createdAt, updatedAt);
    }

    public Idea withDescription(Description newDescription) {
        return new Idea(id, title, newDescription, tags, status, createdAt, updatedAt);
    }

    public Idea withTags(Set<Tag> newTags) {
        return new Idea(id, title, description, newTags, status, createdAt, updatedAt);
    }

    public Idea withStatus(IdeaStatus newStatus) {
        return new Idea(id, title, description, tags, newStatus, createdAt, updatedAt);
    }

    public Idea withUpdatedAt(Instant newUpdatedAt) {
        return new Idea(id, title, description, tags, status, createdAt, newUpdatedAt);
    }
}
