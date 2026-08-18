package com.emgi.timeline.domain.query;

import com.emgi.timeline.domain.model.Idea;
import java.util.Comparator;

/**
 * How the idea list is ordered. Only two options in V1 — "sort by tag" turned out to mean tag
 * filtering, not an ordering (locked decision #3).
 *
 * <p>Phase 5's control is populated from {@link #values()}, so adding an order later extends the UI
 * for free.
 */
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

    /**
     * A <em>total</em> ordering: ideas created in the same millisecond are still ordered, by id.
     *
     * <p>Without the tiebreak, two ideas sharing a {@code createdAt} compare equal, and their
     * relative position can change between sorts — a list that reshuffles itself for no visible
     * reason. The tiebreak is always ascending by id and does not flip with the sort direction,
     * because {@code reversed()} applies only to the key it is called on.
     */
    public Comparator<Idea> comparator() {
        Comparator<Idea> byCreatedAt = Comparator.comparing(Idea::createdAt);
        if (this == NEWEST_FIRST) {
            byCreatedAt = byCreatedAt.reversed();
        }
        return byCreatedAt.thenComparing(idea -> idea.id().value().toString());
    }
}
