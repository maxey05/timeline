package com.emgi.timeline.domain.query;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public record IdeaQuery(
        Optional<String> titleContains,
        Set<Tag> anyOfTags,
        Set<IdeaStatus> anyOfStatus,
        SortOrder sortOrder
) {

    public IdeaQuery {
        Objects.requireNonNull(titleContains, "titleContains");
        Objects.requireNonNull(anyOfTags, "anyOfTags");
        Objects.requireNonNull(anyOfStatus, "anyOfStatus");
        Objects.requireNonNull(sortOrder, "sortOrder");
        titleContains = titleContains.map(String::strip).filter(term -> !term.isEmpty());
        anyOfTags = Set.copyOf(anyOfTags);
        anyOfStatus = Set.copyOf(anyOfStatus);
    }

    public static IdeaQuery all() {
        return new IdeaQuery(Optional.empty(), Set.of(), Set.of(), SortOrder.NEWEST_FIRST);
    }

    public Predicate<Idea> toPredicate() {
        return idea -> matchesTitle(idea) && matchesTags(idea) && matchesStatus(idea);
    }

    private boolean matchesTitle(Idea idea) {
        if (titleContains.isEmpty()) {
            return true;
        }
        String term = titleContains.get().toLowerCase(Locale.ROOT);
        return idea.title().toLowerCase(Locale.ROOT).contains(term);
    }

    private boolean matchesTags(Idea idea) {
        return anyOfTags.isEmpty() || !Collections.disjoint(idea.tags(), anyOfTags);
    }

    private boolean matchesStatus(Idea idea) {
        return anyOfStatus.isEmpty() || anyOfStatus.contains(idea.status());
    }

    public IdeaQuery withTitleContains(String term) {
        return new IdeaQuery(Optional.ofNullable(term), anyOfTags, anyOfStatus, sortOrder);
    }

    public IdeaQuery withTags(Set<Tag> tags) {
        return new IdeaQuery(titleContains, tags, anyOfStatus, sortOrder);
    }

    public IdeaQuery withStatuses(Set<IdeaStatus> statuses) {
        return new IdeaQuery(titleContains, anyOfTags, statuses, sortOrder);
    }

    public IdeaQuery withSortOrder(SortOrder order) {
        return new IdeaQuery(titleContains, anyOfTags, anyOfStatus, order);
    }
}
