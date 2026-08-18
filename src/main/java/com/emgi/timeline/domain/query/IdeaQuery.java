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

/**
 * What the user currently wants to see (ARCHITECTURE.md §7.4). The rules live here; JavaFX only
 * applies them, via {@code FilteredList.setPredicate(query.toPredicate())} and
 * {@code SortedList.setComparator(query.sortOrder().comparator())}.
 *
 * <p>V1 loads every idea into memory and filters there rather than pushing a {@code WHERE} clause
 * into SQL. For a personal list that is simpler and fast enough; if it ever isn't, the call sites
 * don't change, because they already pass an {@code IdeaQuery} object.
 *
 * <p>Combination rules: <strong>AND across dimensions, OR within one</strong> (locked decision #4).
 * An empty dimension is not a filter and matches everything.
 */
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
        // An empty or whitespace-only search box means "no filter", not "match ideas containing
        // a space". Normalizing here keeps that decision out of the controller.
        titleContains = titleContains.map(String::strip).filter(term -> !term.isEmpty());
        anyOfTags = Set.copyOf(anyOfTags);
        anyOfStatus = Set.copyOf(anyOfStatus);
    }

    /** No filters, newest first — what the list shows on startup and after "clear filters". */
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
        // Substring matching, never regex: a user typing "c++" or "(" must get results, not a
        // PatternSyntaxException (§8 edge cases). Locale.ROOT for the same reason as Tag.
        String term = titleContains.get().toLowerCase(Locale.ROOT);
        return idea.title().toLowerCase(Locale.ROOT).contains(term);
    }

    private boolean matchesTags(Idea idea) {
        // OR within the dimension: any one matching tag is enough. Not containsAll.
        return anyOfTags.isEmpty() || !Collections.disjoint(idea.tags(), anyOfTags);
    }

    private boolean matchesStatus(Idea idea) {
        return anyOfStatus.isEmpty() || anyOfStatus.contains(idea.status());
    }

    // Phase 5's controls each change one dimension at a time.

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
