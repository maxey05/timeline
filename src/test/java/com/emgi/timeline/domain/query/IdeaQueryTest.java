package com.emgi.timeline.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static com.emgi.timeline.support.IdeaFixtures.anIdea;
import static com.emgi.timeline.support.IdeaFixtures.tags;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdeaQueryTest {

    private final Idea javaIdea = anIdea().withIdNumber(1)
            .withTitle("Learn JavaFX").withTags("java", "ui").withStatus(IdeaStatus.IN_PROGRESS).build();
    private final Idea cookingIdea = anIdea().withIdNumber(2)
            .withTitle("Cook more").withTags("life").withStatus(IdeaStatus.INCOMPLETE).build();
    private final Idea doneIdea = anIdea().withIdNumber(3)
            .withTitle("Ship the app").withTags("java").withStatus(IdeaStatus.COMPLETED).build();

    private List<Idea> all() {
        return List.of(javaIdea, cookingIdea, doneIdea);
    }

    private List<Idea> matching(IdeaQuery query) {
        Predicate<Idea> predicate = query.toPredicate();
        return all().stream().filter(predicate).toList();
    }

    @Test
    void defaultQueryMatchesEverythingAndSortsNewestFirst() {
        IdeaQuery query = IdeaQuery.all();

        assertThat(matching(query)).containsExactlyElementsOf(all());
        assertThat(query.sortOrder()).isEqualTo(SortOrder.NEWEST_FIRST);
    }

    @Test
    void filtersByTitleSubstring() {
        assertThat(matching(IdeaQuery.all().withTitleContains("Cook"))).containsExactly(cookingIdea);
    }

    @Test
    @DisplayName("title matching is case-insensitive")
    void titleMatchIsCaseInsensitive() {
        assertThat(matching(IdeaQuery.all().withTitleContains("javafx"))).containsExactly(javaIdea);
        assertThat(matching(IdeaQuery.all().withTitleContains("JAVAFX"))).containsExactly(javaIdea);
    }

    @Test
    @DisplayName("a search term is matched literally, never compiled as a regex")
    void titleMatchTreatsMetacharactersLiterally() {
        Idea regexish = anIdea().withIdNumber(9).withTitle("Learn C++ (properly)").build();
        Predicate<Idea> predicate = IdeaQuery.all().withTitleContains("C++ (").toPredicate();

        assertThat(predicate.test(regexish)).isTrue();
        assertThat(predicate.test(javaIdea)).isFalse();
        assertThat(IdeaQuery.all().withTitleContains(".*").toPredicate().test(regexish)).isFalse();
    }

    @Test
    @DisplayName("an empty or whitespace-only search term is not a filter")
    void blankTitleTermIsNormalizedAway() {
        assertThat(IdeaQuery.all().withTitleContains("").titleContains()).isEmpty();
        assertThat(IdeaQuery.all().withTitleContains("   ").titleContains()).isEmpty();
        assertThat(matching(IdeaQuery.all().withTitleContains("  "))).containsExactlyElementsOf(all());
    }

    @Test
    void searchTermIsTrimmed() {
        assertThat(IdeaQuery.all().withTitleContains("  Cook  ").titleContains()).contains("Cook");
    }

    @Test
    void filtersByASingleTag() {
        assertThat(matching(IdeaQuery.all().withTags(tags("life")))).containsExactly(cookingIdea);
    }

    @Test
    @DisplayName("tags combine with OR — an idea needs any one of them, not all")
    void tagsCombineWithOr() {
        assertThat(matching(IdeaQuery.all().withTags(tags("life", "ui"))))
                .containsExactly(javaIdea, cookingIdea);
    }

    @Test
    void tagFilterUsesCanonicalForm() {
        assertThat(matching(IdeaQuery.all().withTags(Set.of(Tag.of("  JAVA  ")))))
                .containsExactly(javaIdea, doneIdea);
    }

    @Test
    void unknownTagMatchesNothing() {
        assertThat(matching(IdeaQuery.all().withTags(tags("nonexistent")))).isEmpty();
    }

    @Test
    void filtersByStatus() {
        assertThat(matching(IdeaQuery.all().withStatuses(Set.of(IdeaStatus.COMPLETED))))
                .containsExactly(doneIdea);
    }

    @Test
    void statusesCombineWithOr() {
        assertThat(matching(IdeaQuery.all()
                .withStatuses(Set.of(IdeaStatus.COMPLETED, IdeaStatus.INCOMPLETE))))
                .containsExactly(cookingIdea, doneIdea);
    }

    @Test
    @DisplayName("dimensions combine with AND — every filter must pass")
    void dimensionsCombineWithAnd() {
        IdeaQuery query = IdeaQuery.all()
                .withTags(tags("java"))
                .withStatuses(Set.of(IdeaStatus.COMPLETED));

        assertThat(matching(query)).containsExactly(doneIdea);
    }

    @Test
    void anEmptyIntersectionMatchesNothing() {
        IdeaQuery query = IdeaQuery.all()
                .withTitleContains("Cook")
                .withTags(tags("java"));

        assertThat(matching(query)).isEmpty();
    }

    @Test
    void withMethodsChangeOnlyTheirOwnDimension() {
        IdeaQuery query = IdeaQuery.all()
                .withTitleContains("Ship")
                .withTags(tags("java"))
                .withStatuses(Set.of(IdeaStatus.COMPLETED))
                .withSortOrder(SortOrder.OLDEST_FIRST);

        assertThat(query.titleContains()).contains("Ship");
        assertThat(query.anyOfTags()).containsExactly(Tag.of("java"));
        assertThat(query.anyOfStatus()).containsExactly(IdeaStatus.COMPLETED);
        assertThat(query.sortOrder()).isEqualTo(SortOrder.OLDEST_FIRST);
    }

    @Test
    void queryIsImmutable() {
        IdeaQuery query = IdeaQuery.all().withTags(tags("java"));

        assertThat(query.anyOfTags()).isNotSameAs(tags("java"));
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> query.anyOfTags().add(Tag.of("new")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullComponents() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new IdeaQuery(null, Set.of(), Set.of(), SortOrder.NEWEST_FIRST))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new IdeaQuery(Optional.empty(), Set.of(), Set.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
