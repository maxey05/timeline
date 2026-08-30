package com.emgi.timeline.domain.query;

import com.emgi.timeline.domain.model.Idea;
import java.util.Comparator;

public enum SortOrder {

    BY_PROGRESS("By progress"),
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

        if(this != OLDEST_FIRST)
            byCreatedAt = byCreatedAt.reversed();

        Comparator<Idea> primary = this == BY_PROGRESS
            ? Comparator.comparingInt(SortOrder::groupRank).thenComparing(byCreatedAt)
            : byCreatedAt;

        return primary.thenComparing(idea -> idea.id().value().toString());
    }

    private static int groupRank(Idea idea)
    {
        return switch (idea.status())
        {
            case IN_PROGRESS -> 0;
            case INCOMPLETE -> 1;
            case COMPLETED -> 2;
        };
    }
}
