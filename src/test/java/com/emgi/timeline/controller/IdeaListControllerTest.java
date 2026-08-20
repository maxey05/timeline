package com.emgi.timeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.query.IdeaQuery;
import com.emgi.timeline.domain.query.SortOrder;
import com.emgi.timeline.domain.validation.IdeaValidator;
import com.emgi.timeline.repository.IdeaRepository;
import com.emgi.timeline.repository.InMemoryIdeaRepository;
import com.emgi.timeline.service.IdeaService;
import com.emgi.timeline.support.FixedClock;
import com.emgi.timeline.support.IdeaFixtures;
import com.emgi.timeline.support.RecordingIdeaRepository;
import com.emgi.timeline.support.SequentialIdGenerator;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The §7.4 wiring, tested with no toolkit running — which is the whole reason controllers are
 * forbidden from touching the scene graph (see {@link ControllerPurityTest}).
 */
@DisplayName("IdeaListController")
class IdeaListControllerTest {

    private static final Idea OLDEST =
            IdeaFixtures.anIdea().withIdNumber(1).withTitle("Oldest idea")
                    .createdAt("2026-01-01T00:00:00Z").build();
    private static final Idea MIDDLE =
            IdeaFixtures.anIdea().withIdNumber(2).withTitle("Middle idea")
                    .createdAt("2026-02-01T00:00:00Z").build();
    private static final Idea NEWEST =
            IdeaFixtures.anIdea().withIdNumber(3).withTitle("Newest idea")
                    .createdAt("2026-03-01T00:00:00Z").build();

    private InMemoryIdeaRepository repository;
    private IdeaListController controller;

    @BeforeEach
    void setUp() {
        repository = new InMemoryIdeaRepository();
        controller = new IdeaListController(serviceOn(repository));
    }

    private static IdeaService serviceOn(IdeaRepository repository) {
        return new IdeaService(repository, new IdeaValidator(), new SequentialIdGenerator(),
                FixedClock.atDefault());
    }

    /** Saves straight to the repository so each idea can have a createdAt of its own. */
    private void store(Idea... ideas) {
        for (Idea idea : ideas) {
            repository.save(idea);
        }
    }

    @Test
    @DisplayName("the list is empty but usable before load — the view binds before data arrives")
    void ideasIsEmptyBeforeLoad() {
        assertThat(controller.ideas()).isNotNull();
        assertThat(controller.ideas()).isEmpty();
    }

    @Test
    @DisplayName("the view always holds the same live list instance")
    void ideasReturnsTheSameLiveInstance() {
        ObservableList<Idea> before = controller.ideas();
        store(NEWEST);
        controller.load();

        assertThat(controller.ideas()).isSameAs(before);
        assertThat(before).hasSize(1);
    }

    @Test
    @DisplayName("load populates the list from the service")
    void loadPopulatesFromTheService() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();

        assertThat(controller.ideas()).hasSize(3);
    }

    @Test
    @DisplayName("the default order is newest first, with nobody setting a comparator")
    void defaultOrderIsNewestFirst() {
        store(OLDEST, NEWEST, MIDDLE);
        controller.load();

        assertThat(controller.ideas()).containsExactly(NEWEST, MIDDLE, OLDEST);
    }

    @Test
    @DisplayName("the default query is the unfiltered one")
    void queryDefaultsToAll() {
        assertThat(controller.query()).isEqualTo(IdeaQuery.all());
    }

    @Test
    @DisplayName("loading twice does not duplicate the list")
    void loadIsIdempotent() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();
        controller.load();

        assertThat(controller.ideas()).hasSize(3);
    }

    @Test
    @DisplayName("load picks up ideas added since the last load, and drops deleted ones")
    void loadPicksUpLaterChanges() {
        store(OLDEST);
        controller.load();

        store(NEWEST);
        controller.load();
        assertThat(controller.ideas()).containsExactly(NEWEST, OLDEST);

        repository.delete(OLDEST.id());
        controller.load();
        assertThat(controller.ideas()).containsExactly(NEWEST);
    }

    @Test
    @DisplayName("filtering happens in memory, not by re-querying storage")
    void setQueryFiltersInMemory() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();

        // Nothing is left in storage. If filtering re-queried, the list would empty out.
        repository.delete(OLDEST.id());
        repository.delete(MIDDLE.id());
        repository.delete(NEWEST.id());

        controller.setQuery(IdeaQuery.all().withTitleContains("middle"));
        assertThat(controller.ideas()).containsExactly(MIDDLE);

        controller.setQuery(IdeaQuery.all());
        assertThat(controller.ideas()).containsExactly(NEWEST, MIDDLE, OLDEST);
    }

    @Test
    @DisplayName("changing the sort order reverses the list")
    void setQueryChangesOrder() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();

        controller.setQuery(IdeaQuery.all().withSortOrder(SortOrder.OLDEST_FIRST));

        assertThat(controller.ideas()).containsExactly(OLDEST, MIDDLE, NEWEST);
        assertThat(controller.query().sortOrder()).isEqualTo(SortOrder.OLDEST_FIRST);
    }

    @Test
    @DisplayName("filter and sort combine: the filter runs first, the survivors are ordered")
    void filterAndSortCombine() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();

        controller.setQuery(IdeaQuery.all()
                .withTitleContains("idea")
                .withSortOrder(SortOrder.OLDEST_FIRST));

        assertThat(controller.ideas()).containsExactly(OLDEST, MIDDLE, NEWEST);
    }

    @Test
    @DisplayName("reading the list never writes to the repository — Phase 3 is read-only")
    void readingTheListNeverWrites() {
        InMemoryIdeaRepository backing = new InMemoryIdeaRepository();
        backing.save(OLDEST);
        backing.save(NEWEST);

        RecordingIdeaRepository recording = new RecordingIdeaRepository(backing);
        IdeaListController readOnly = new IdeaListController(serviceOn(recording));

        readOnly.load();
        readOnly.setQuery(IdeaQuery.all().withTitleContains("newest"));
        readOnly.load();

        assertThat(recording.saved()).isEmpty();
        assertThat(recording.deleted()).isEmpty();
    }

    @Test
    @DisplayName("a null query is rejected")
    void setQueryRejectsNull() {
        assertThatThrownBy(() -> controller.setQuery(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a null service is rejected at construction")
    void rejectsNullService() {
        assertThatThrownBy(() -> new IdeaListController(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- Phase 4: mutations ----

    @Test
    @DisplayName("add puts the idea in the list")
    void addPutsTheIdeaInTheList() {
        controller.add(NEWEST);

        assertThat(controller.ideas()).containsExactly(NEWEST);
    }

    @Test
    @DisplayName("add respects the current sort — an older idea does not jump to the top")
    void addRespectsTheCurrentSort() {
        store(MIDDLE, NEWEST);
        controller.load();

        controller.add(OLDEST);

        assertThat(controller.ideas()).containsExactly(NEWEST, MIDDLE, OLDEST);
    }

    @Test
    @DisplayName("add respects the current filter — a non-matching idea stays out of the view")
    void addRespectsTheCurrentFilter() {
        store(MIDDLE);
        controller.load();
        controller.setQuery(IdeaQuery.all().withTitleContains("middle"));

        controller.add(NEWEST);

        assertThat(controller.ideas()).containsExactly(MIDDLE);
    }

    @Test
    @DisplayName("replace swaps the edited idea in without changing the list's size")
    void replaceSwapsInPlace() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();

        controller.replace(MIDDLE.withTitle("Edited middle"));

        assertThat(controller.ideas()).hasSize(3);
        assertThat(titles()).contains("Edited middle");
        assertThat(titles()).doesNotContain("Middle idea");
    }

    @Test
    @DisplayName("replace keeps the idea's position, so selection and scroll survive an edit")
    void replaceKeepsTheIdeasPosition() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();
        int before = controller.ideas().indexOf(MIDDLE);

        Idea edited = MIDDLE.withTitle("Edited middle");
        controller.replace(edited);

        assertThat(controller.ideas().indexOf(edited)).isEqualTo(before);
    }

    @Test
    @DisplayName("replace rejects an id that is not in the list — that is a wiring bug")
    void replaceRejectsAnUnknownId() {
        store(OLDEST);
        controller.load();

        assertThatThrownBy(() -> controller.replace(NEWEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(controller.ideas()).containsExactly(OLDEST);
    }

    @Test
    @DisplayName("delete removes the idea from storage and from the list")
    void deleteRemovesFromStorageAndFromTheList() {
        InMemoryIdeaRepository backing = new InMemoryIdeaRepository();
        backing.save(OLDEST);
        backing.save(NEWEST);

        RecordingIdeaRepository recording = new RecordingIdeaRepository(backing);
        IdeaListController writing = new IdeaListController(serviceOn(recording));
        writing.load();

        assertThat(writing.delete(OLDEST)).isTrue();

        assertThat(recording.deleted()).containsExactly(OLDEST.id());
        assertThat(writing.ideas()).containsExactly(NEWEST);
        assertThat(backing.findById(OLDEST.id())).isEmpty();
    }

    @Test
    @DisplayName("deleting something storage no longer holds still drops the row, and does not throw")
    void deleteOfSomethingAlreadyGoneStillDropsTheRow() {
        controller.add(OLDEST);

        assertThat(controller.delete(OLDEST)).isFalse();
        assertThat(controller.ideas()).isEmpty();
    }

    @Test
    @DisplayName("the mutation methods reject null")
    void mutationsRejectNull() {
        assertThatThrownBy(() -> controller.add(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> controller.replace(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> controller.delete(null)).isInstanceOf(NullPointerException.class);
    }

    /** The titles currently on screen, in order. */
    private java.util.List<String> titles() {
        return controller.ideas().stream().map(Idea::title).toList();
    }
}
