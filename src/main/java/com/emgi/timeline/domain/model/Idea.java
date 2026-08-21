package com.emgi.timeline.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

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
        tags = Set.copyOf(tags);
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
