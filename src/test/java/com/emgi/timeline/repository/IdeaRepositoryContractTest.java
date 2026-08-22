package com.emgi.timeline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.support.IdeaFixtures;
import com.emgi.timeline.support.SequentialIdGenerator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

abstract class IdeaRepositoryContractTest {

    private IdeaRepository repository;

    protected abstract IdeaRepository createRepository();

    protected final IdeaRepository repository() {
        return repository;
    }

    @BeforeEach
    void createRepositoryUnderTest() {
        repository = createRepository();
    }

    @Test
    @DisplayName("a fresh repository is empty")
    void freshRepositoryIsEmpty() {
        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.count()).isEqualTo(0L);
        assertThat(repository.findById(SequentialIdGenerator.idFor(1))).isEmpty();
    }

    @Test
    @DisplayName("a saved idea comes back equal to what went in")
    void savedIdeaRoundTrips() {
        Idea idea = IdeaFixtures.anIdea()
                .withIdNumber(1)
                .withTitle("Write the storage layer")
                .withText("Contract tests first.")
                .withTags("java", "timeline")
                .withStatus(IdeaStatus.IN_PROGRESS)
                .build();

        repository.save(idea);

        assertThat(repository.findById(idea.id())).contains(idea);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findById on an unknown id is empty, not an exception")
    void findByIdUnknownIsEmpty() {
        repository.save(IdeaFixtures.anIdea().withIdNumber(1).build());

        assertThat(repository.findById(SequentialIdGenerator.idFor(2))).isEmpty();
    }

    @Test
    @DisplayName("findAll returns every saved idea")
    void findAllReturnsEverything() {
        Idea first = IdeaFixtures.anIdea().withIdNumber(1).withTitle("First").build();
        Idea second = IdeaFixtures.anIdea().withIdNumber(2).withTitle("Second").build();
        Idea third = IdeaFixtures.anIdea().withIdNumber(3).withTitle("Third").build();

        repository.save(first);
        repository.save(second);
        repository.save(third);

        assertThat(repository.findAll()).containsExactlyInAnyOrder(first, second, third);
        assertThat(repository.count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("saving an existing id updates it in place instead of duplicating it")
    void saveUpserts() {
        Idea original = IdeaFixtures.anIdea().withIdNumber(1).withTitle("Draft").build();
        repository.save(original);

        Idea edited = original.withTitle("Final").withStatus(IdeaStatus.COMPLETED);
        repository.save(edited);

        assertThat(repository.count()).isEqualTo(1L);
        assertThat(repository.findById(original.id())).contains(edited);
    }

    @Test
    @DisplayName("delete removes the idea and reports that it did")
    void deleteRemoves() {
        Idea idea = IdeaFixtures.anIdea().withIdNumber(1).build();
        repository.save(idea);

        assertThat(repository.delete(idea.id())).isTrue();
        assertThat(repository.findById(idea.id())).isEmpty();
        assertThat(repository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("deleting an unknown id is a no-op, not an exception")
    void deleteUnknownIsNoOp() {
        Idea idea = IdeaFixtures.anIdea().withIdNumber(1).build();
        repository.save(idea);

        assertThat(repository.delete(SequentialIdGenerator.idFor(2))).isFalse();
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a description survives a round trip byte for byte, tokens and newlines included")
    void descriptionRoundTripsVerbatim() {
        String body = "Intro paragraph.\n\n"
                + "See https://example.com/spec for the details.\n"
                + "![Layer diagram](file:///home/emgi/diagram.png)\n"
                + "Closing note.";
        Idea idea = IdeaFixtures.anIdea()
                .withIdNumber(1)
                .withDescription(Description.ofText(body))
                .build();

        repository.save(idea);

        Idea loaded = repository.findById(idea.id()).orElseThrow();
        assertThat(loaded.description().text()).isEqualTo(body);
    }

    @Test
    @DisplayName("tags survive a round trip with their normalization intact")
    void tagsRoundTrip() {
        Idea idea = IdeaFixtures.anIdea()
                .withIdNumber(1)
                .withTags("Java", "  Idea   Manager  ", "java")
                .build();
        assertThat(idea.tags()).hasSize(2);

        repository.save(idea);

        assertThat(repository.findById(idea.id()).orElseThrow().tags())
                .containsExactlyInAnyOrderElementsOf(idea.tags());
    }

    @Test
    @DisplayName("an idea with no tags and an empty description round trips")
    void emptyCollectionsRoundTrip() {
        Idea idea = IdeaFixtures.anIdea()
                .withIdNumber(1)
                .withTags()
                .withDescription(Description.empty())
                .build();

        repository.save(idea);

        Idea loaded = repository.findById(idea.id()).orElseThrow();
        assertThat(loaded.tags()).isEmpty();
        assertThat(loaded.description().text()).isEmpty();
        assertThat(loaded).isEqualTo(idea);
    }

    @Test
    @DisplayName("re-saving replaces tags and the description rather than accumulating them")
    void resaveReplacesChildCollections() {
        Idea idea = IdeaFixtures.anIdea()
                .withIdNumber(1)
                .withTags("one", "two", "three")
                .withText("a\n\nb\n\nc")
                .build();
        repository.save(idea);

        Idea trimmed = idea
                .withTags(IdeaFixtures.tags("only"))
                .withDescription(Description.ofText("just one line"));
        repository.save(trimmed);

        Idea loaded = repository.findById(idea.id()).orElseThrow();
        assertThat(loaded.tags()).containsExactlyElementsOf(IdeaFixtures.tags("only"));
        assertThat(loaded.description().text()).isEqualTo("just one line");
    }

    @Test
    @DisplayName("non-ASCII text survives a round trip unchanged")
    void nonAsciiRoundTrips() {
        Idea idea = IdeaFixtures.anIdea()
                .withIdNumber(1)
                .withTitle("想法 — first draft 🎉")
                .withText("表意文字とemojiが混ざっても壊れない 🎉")
                .withTags("日本語", "ideas")
                .build();

        repository.save(idea);

        assertThat(repository.findById(idea.id())).contains(idea);
    }

    @Test
    @DisplayName("findAll does not leak a mutable reference to internal state")
    void findAllIsImmutable() {
        repository.save(IdeaFixtures.anIdea().withIdNumber(1).build());
        List<Idea> ideas = repository.findAll();

        assertThatThrownBy(() -> ideas.add(IdeaFixtures.anIdea().withIdNumber(2).build()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("count tracks saves and deletes")
    void countTracksWrites() {
        assertThat(repository.count()).isEqualTo(0L);
        repository.save(IdeaFixtures.anIdea().withIdNumber(1).build());
        repository.save(IdeaFixtures.anIdea().withIdNumber(2).build());
        assertThat(repository.count()).isEqualTo(2L);

        repository.delete(SequentialIdGenerator.idFor(1));

        assertThat(repository.count()).isEqualTo(1L);
    }
}
