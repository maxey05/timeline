package com.emgi.timeline.domain.query;

import com.emgi.timeline.domain.model.Idea;
import java.util.Comparator;

public enum SortOrder {

    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first");

    private final String displayName;

    SortOrder(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public Comparator<Idea> comparator() {
        Comparator<Idea> byCreatedAt = Comparator.comparing(Idea::createdAt);
        if (this == NEWEST_FIRST) {
            byCreatedAt = byCreatedAt.reversed();
        }
        return byCreatedAt.thenComparing(idea -> idea.id().value().toString());
    }
}
