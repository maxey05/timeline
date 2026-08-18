package com.emgi.timeline.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.emgi.timeline.support.IdeaFixtures.anIdea;

import com.emgi.timeline.support.IdeaFixtures;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdeaTest {

    private static final Instant LATER = IdeaFixtures.T0.plusSeconds(3600);

    @Test
    void rejectsNullComponents() {
        Idea idea = anIdea().build();

        assertThatThrownBy(() -> idea.withTitle(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> idea.withDescription(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> idea.withTags(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> idea.withStatus(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> idea.withUpdatedAt(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("with* returns a new instance and leaves the original untouched")
    void withMethodsDoNotMutate() {
        Idea original = anIdea().withTitle("Original").build();

        Idea renamed = original.withTitle("Renamed");

        assertThat(renamed).isNotSameAs(original);
        assertThat(renamed.title()).isEqualTo("Renamed");
        assertThat(original.title()).isEqualTo("Original");
    }

    @Test
    void withMethodsChangeOnlyTheirOwnField() {
        Idea original = anIdea().withTitle("T").withTags("java").build();

        Idea updated = original.withStatus(IdeaStatus.COMPLETED);

        assertThat(updated.status()).isEqualTo(IdeaStatus.COMPLETED);
        assertThat(updated.title()).isEqualTo(original.title());
        assertThat(updated.tags()).isEqualTo(original.tags());
        assertThat(updated.description()).isEqualTo(original.description());
        assertThat(updated.id()).isEqualTo(original.id());
    }

    @Test
    @DisplayName("createdAt survives an updatedAt change — there is no withCreatedAt on purpose")
    void updatingTimestampPreservesCreatedAt() {
        Idea original = anIdea().build();

        Idea updated = original.withUpdatedAt(LATER);

        assertThat(updated.createdAt()).isEqualTo(IdeaFixtures.T0);
        assertThat(updated.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("tags passed in are copied, so mutating the caller's set cannot change the idea")
    void tagsAreDefensivelyCopied() {
        Set<Tag> mutable = new HashSet<>(Set.of(Tag.of("java")));
        Idea idea = anIdea().withTags(mutable).build();

        mutable.add(Tag.of("javafx"));

        assertThat(idea.tags()).containsExactly(Tag.of("java"));
    }

    @Test
    void exposedTagsCannotBeModified() {
        Idea idea = anIdea().withTags("java").build();

        assertThatThrownBy(() -> idea.tags().add(Tag.of("javafx")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("equality is by value, which is what lets the list replace an item by content")
    void ideasWithEqualComponentsAreEqual() {
        Idea one = anIdea().withIdNumber(7).withTitle("Same").build();
        Idea two = anIdea().withIdNumber(7).withTitle("Same").build();

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
    }
}
