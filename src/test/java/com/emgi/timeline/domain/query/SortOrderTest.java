package com.emgi.timeline.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static com.emgi.timeline.support.IdeaFixtures.anIdea;

import com.emgi.timeline.domain.model.Idea;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SortOrderTest {

    private final Idea oldest = anIdea().withIdNumber(1).withTitle("oldest")
            .createdAt("2026-01-01T00:00:00Z").build();
    private final Idea middle = anIdea().withIdNumber(2).withTitle("middle")
            .createdAt("2026-02-01T00:00:00Z").build();
    private final Idea newest = anIdea().withIdNumber(3).withTitle("newest")
            .createdAt("2026-03-01T00:00:00Z").build();

    private List<Idea> sortedBy(SortOrder order) {
        List<Idea> ideas = new ArrayList<>(List.of(middle, newest, oldest));
        ideas.sort(order.comparator());
        return ideas;
    }

    @Test
    void newestFirstPutsTheMostRecentAtTheTop() {
        assertThat(sortedBy(SortOrder.NEWEST_FIRST)).containsExactly(newest, middle, oldest);
    }

    @Test
    void oldestFirstPutsTheEarliestAtTheTop() {
        assertThat(sortedBy(SortOrder.OLDEST_FIRST)).containsExactly(oldest, middle, newest);
    }

    @Test
    @DisplayName("ideas sharing a createdAt are still ordered — by id, so the list never jitters")
    void tiebreaksOnIdWhenTimestampsAreIdentical() {
        Idea first = anIdea().withIdNumber(1).createdAt("2026-01-01T00:00:00Z").build();
        Idea second = anIdea().withIdNumber(2).createdAt("2026-01-01T00:00:00Z").build();

        List<Idea> newestFirst = new ArrayList<>(List.of(second, first));
        newestFirst.sort(SortOrder.NEWEST_FIRST.comparator());

        List<Idea> oldestFirst = new ArrayList<>(List.of(second, first));
        oldestFirst.sort(SortOrder.OLDEST_FIRST.comparator());

        // The id tiebreak is ascending in both directions — it does not flip with the sort order.
        assertThat(newestFirst).containsExactly(first, second);
        assertThat(oldestFirst).containsExactly(first, second);
    }

    @Test
    @DisplayName("the comparator is total: no two distinct ideas compare equal")
    void comparatorIsTotal() {
        List<Idea> sameInstant = List.of(
                anIdea().withIdNumber(1).createdAt("2026-01-01T00:00:00Z").build(),
                anIdea().withIdNumber(2).createdAt("2026-01-01T00:00:00Z").build(),
                anIdea().withIdNumber(3).createdAt("2026-01-01T00:00:00Z").build());

        for (SortOrder order : SortOrder.values()) {
            Comparator<Idea> comparator = order.comparator();
            for (Idea a : sameInstant) {
                for (Idea b : sameInstant) {
                    if (a.equals(b)) {
                        assertThat(comparator.compare(a, b)).isZero();
                    } else {
                        assertThat(comparator.compare(a, b)).isNotZero();
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("sorting is stable across repeated runs of the same input in a different order")
    void sortingIsDeterministic() {
        List<Idea> a = new ArrayList<>(List.of(oldest, middle, newest));
        List<Idea> b = new ArrayList<>(List.of(newest, oldest, middle));

        a.sort(SortOrder.NEWEST_FIRST.comparator());
        b.sort(SortOrder.NEWEST_FIRST.comparator());

        assertThat(a).isEqualTo(b);
    }

    @Test
    void everyOrderHasADisplayName() {
        for (SortOrder order : SortOrder.values()) {
            assertThat(order.displayName()).isNotBlank();
        }
    }
}
