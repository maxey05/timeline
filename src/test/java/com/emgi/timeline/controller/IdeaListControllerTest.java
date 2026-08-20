package com.emgi.timeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.Tag;
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
import java.util.ArrayList;
import java.util.List;
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

    // Phase 5 works on tags and titles, so it needs ideas that differ in both.
    private static final Idea SCHEDULER =
            IdeaFixtures.anIdea().withIdNumber(11).withTitle("Rewrite the scheduler")
                    .withTags("java", "school").createdAt("2026-04-01T00:00:00Z").build();
    private static final Idea PORTFOLIO =
            IdeaFixtures.anIdea().withIdNumber(12).withTitle("Portfolio site ideas")
                    .withTags("web").createdAt("2026-05-01T00:00:00Z").build();
    private static final Idea NOTES =
            IdeaFixtures.anIdea().withIdNumber(13).withTitle("Lecture notes cleanup")
                    .withTags("school").createdAt("2026-06-01T00:00:00Z").build();

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


    // ---- Phase 5: search ----

    @Test
    @DisplayName("the search box filters by title substring")
    void searchFiltersByTitleSubstring() {
        store(SCHEDULER, PORTFOLIO);
        controller.load();

        controller.searchTextProperty().set("sched");

        assertThat(controller.ideas()).containsExactly(SCHEDULER);
    }

    @Test
    @DisplayName("search is case-insensitive in both directions")
    void searchIsCaseInsensitive() {
        store(SCHEDULER, PORTFOLIO);
        controller.load();

        controller.searchTextProperty().set("SCHED");
        assertThat(controller.ideas()).containsExactly(SCHEDULER);

        controller.searchTextProperty().set("sched");
        assertThat(controller.ideas()).containsExactly(SCHEDULER);
    }

    @Test
    @DisplayName("regex metacharacters in the search box are matched literally, not compiled")
    void searchTreatsMetacharactersLiterally() {
        Idea awkward = IdeaFixtures.anIdea().withIdNumber(14).withTitle("c++ (draft)")
                .createdAt("2026-07-01T00:00:00Z").build();
        store(awkward, PORTFOLIO);
        controller.load();

        // A PatternSyntaxException here would mean someone reached for Pattern.compile (§8).
        controller.searchTextProperty().set("c++ (");

        assertThat(controller.ideas()).containsExactly(awkward);
    }

    @Test
    @DisplayName("a whitespace-only search is not a filter")
    void blankSearchIsNotAFilter() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        controller.searchTextProperty().set("   ");

        assertThat(controller.ideas()).hasSize(3);
        assertThat(controller.query().titleContains()).isEmpty();
    }

    @Test
    @DisplayName("clearing the search box restores every idea")
    void clearingSearchRestoresEverything() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        controller.searchTextProperty().set("portfolio");
        assertThat(controller.ideas()).hasSize(1);

        controller.searchTextProperty().set("");
        assertThat(controller.ideas()).hasSize(3);
    }

    @Test
    @DisplayName("search matches non-ASCII titles, case-insensitively and locale-safely")
    void searchMatchesNonAsciiTitles() {
        Idea cjk = IdeaFixtures.anIdea().withIdNumber(15).withTitle("想法管理器 🚀")
                .createdAt("2026-07-01T00:00:00Z").build();
        Idea umlaut = IdeaFixtures.anIdea().withIdNumber(16).withTitle("Übung notes")
                .createdAt("2026-07-02T00:00:00Z").build();
        store(cjk, umlaut);
        controller.load();

        controller.searchTextProperty().set("管理");
        assertThat(controller.ideas()).containsExactly(cjk);

        controller.searchTextProperty().set("übung");
        assertThat(controller.ideas()).containsExactly(umlaut);
    }

    // ---- Phase 5: tags ----

    @Test
    @DisplayName("available tags are the union of every tag in use")
    void availableTagsIsTheUnionOfEveryTagInUse() {
        store(SCHEDULER, PORTFOLIO);
        controller.load();

        assertThat(controller.availableTags()).hasSize(3);
    }

    @Test
    @DisplayName("available tags are sorted by name and carry no duplicates")
    void availableTagsIsSortedByNameAndDeduplicated() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        // "school" is on two ideas and appears once; the order is the chip row's order.
        assertThat(controller.availableTags())
                .containsExactly(Tag.of("java"), Tag.of("school"), Tag.of("web"));
    }

    @Test
    @DisplayName("adding an idea registers its tags without a reload")
    void availableTagsUpdatesWhenAnIdeaIsAdded() {
        store(SCHEDULER);
        controller.load();
        assertThat(controller.availableTags()).doesNotContain(Tag.of("web"));

        controller.add(PORTFOLIO);

        assertThat(controller.availableTags())
                .containsExactly(Tag.of("java"), Tag.of("school"), Tag.of("web"));
    }

    @Test
    @DisplayName("deleting the last idea using a tag drops its chip")
    void availableTagsUpdatesWhenAnIdeaIsDeleted() {
        store(SCHEDULER, PORTFOLIO);
        controller.load();

        controller.delete(PORTFOLIO);

        assertThat(controller.availableTags()).containsExactly(Tag.of("java"), Tag.of("school"));
    }

    @Test
    @DisplayName("selecting a tag narrows the list to the ideas carrying it")
    void selectingOneTagFiltersToIt() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        controller.selectedTags().add(Tag.of("web"));

        assertThat(controller.ideas()).containsExactly(PORTFOLIO);
    }

    @Test
    @DisplayName("two selected tags combine with OR, not AND — locked decision #4")
    void selectingTwoTagsCombinesWithOr() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        controller.selectedTags().addAll(Tag.of("java"), Tag.of("web"));

        // SCHEDULER has java (not web) and PORTFOLIO has web (not java) — containsAll would show
        // neither. NOTES has only school and stays out.
        assertThat(controller.ideas()).containsExactly(PORTFOLIO, SCHEDULER);
    }

    @Test
    @DisplayName("deleting the last idea with a selected tag drops the chip and lifts the filter")
    void deletingTheLastIdeaWithASelectedTagLiftsTheFilter() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();
        controller.selectedTags().add(Tag.of("web"));
        assertThat(controller.ideas()).containsExactly(PORTFOLIO);

        controller.delete(PORTFOLIO);

        // §8: not "silently show zero results forever".
        assertThat(controller.availableTags()).doesNotContain(Tag.of("web"));
        assertThat(controller.selectedTags()).isEmpty();
        assertThat(controller.ideas()).containsExactly(NOTES, SCHEDULER);
    }

    // ---- Phase 5: sort ----

    @Test
    @DisplayName("the sort property drives the order")
    void sortOrderPropertyDrivesTheOrder() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();

        controller.sortOrderProperty().set(SortOrder.OLDEST_FIRST);

        assertThat(controller.ideas()).containsExactly(OLDEST, MIDDLE, NEWEST);
    }

    @Test
    @DisplayName("the chosen sort survives a filter change")
    void sortOrderSurvivesAFilterChange() {
        store(OLDEST, MIDDLE, NEWEST);
        controller.load();
        controller.sortOrderProperty().set(SortOrder.OLDEST_FIRST);

        controller.searchTextProperty().set("idea");

        assertThat(controller.ideas()).containsExactly(OLDEST, MIDDLE, NEWEST);
        assertThat(controller.sortOrderProperty().get()).isEqualTo(SortOrder.OLDEST_FIRST);
    }

    @Test
    @DisplayName("two ideas sharing a createdAt keep a stable order across re-sorts")
    void sortIsStableWhenTwoIdeasShareCreatedAt() {
        Idea first = IdeaFixtures.anIdea().withIdNumber(1).withTitle("Twin one")
                .createdAt("2026-03-01T00:00:00Z").build();
        Idea second = IdeaFixtures.anIdea().withIdNumber(2).withTitle("Twin two")
                .createdAt("2026-03-01T00:00:00Z").build();
        store(second, first);
        controller.load();

        List<Idea> before = new ArrayList<>(controller.ideas());

        // Re-apply the query a few times; nothing about the pair changed, so nothing may move.
        controller.searchTextProperty().set("twin");
        controller.searchTextProperty().set("");

        assertThat(controller.ideas()).containsExactlyElementsOf(before);
        assertThat(controller.ideas()).containsExactly(first, second);
    }

    // ---- Phase 5: the dimensions together ----

    @Test
    @DisplayName("search and tags combine with AND across the two dimensions")
    void searchAndTagsCombineWithAnd() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();
        controller.selectedTags().add(Tag.of("java"));

        controller.searchTextProperty().set("portfolio");
        assertThat(controller.ideas()).isEmpty();

        controller.searchTextProperty().set("scheduler");
        assertThat(controller.ideas()).containsExactly(SCHEDULER);
    }

    @Test
    @DisplayName("the derived query reflects every control")
    void queryReflectsEveryControl() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        controller.searchTextProperty().set("note");
        controller.selectedTags().add(Tag.of("school"));
        controller.sortOrderProperty().set(SortOrder.OLDEST_FIRST);

        assertThat(controller.query().titleContains()).contains("note");
        assertThat(controller.query().anyOfTags()).containsExactly(Tag.of("school"));
        assertThat(controller.query().sortOrder()).isEqualTo(SortOrder.OLDEST_FIRST);
    }

    @Test
    @DisplayName("clear filters empties the search and the tags but keeps the sort order")
    void clearFiltersResetsSearchAndTagsButNotSort() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();
        controller.searchTextProperty().set("portfolio");
        controller.selectedTags().add(Tag.of("web"));
        controller.sortOrderProperty().set(SortOrder.OLDEST_FIRST);

        controller.clearFilters();

        assertThat(controller.searchTextProperty().get()).isEmpty();
        assertThat(controller.selectedTags()).isEmpty();
        assertThat(controller.sortOrderProperty().get()).isEqualTo(SortOrder.OLDEST_FIRST);
        assertThat(controller.ideas()).containsExactly(SCHEDULER, PORTFOLIO, NOTES);
    }

    @Test
    @DisplayName("filterActive tracks the filters only — a sort change is not a filter")
    void filterActiveIsTrueOnlyWhenAFilterIsSet() {
        store(SCHEDULER, PORTFOLIO);
        controller.load();
        assertThat(controller.filterActiveProperty().get()).isFalse();

        controller.sortOrderProperty().set(SortOrder.OLDEST_FIRST);
        assertThat(controller.filterActiveProperty().get()).isFalse();

        controller.searchTextProperty().set("sched");
        assertThat(controller.filterActiveProperty().get()).isTrue();

        controller.searchTextProperty().set("");
        assertThat(controller.filterActiveProperty().get()).isFalse();

        controller.selectedTags().add(Tag.of("web"));
        assertThat(controller.filterActiveProperty().get()).isTrue();

        controller.clearFilters();
        assertThat(controller.filterActiveProperty().get()).isFalse();
    }

    // ---- Phase 5: what the view may and may not touch ----

    @Test
    @DisplayName("allIdeas ignores the filter, so the view can tell 'no ideas' from 'no matches'")
    void allIdeasIgnoresTheFilter() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        controller.searchTextProperty().set("portfolio");

        assertThat(controller.ideas()).hasSize(1);
        assertThat(controller.allIdeas()).hasSize(3);
    }

    @Test
    @DisplayName("allIdeas is a read-only view — only the controller mutates the master list")
    void allIdeasIsUnmodifiable() {
        assertThatThrownBy(() -> controller.allIdeas().add(NEWEST))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("availableTags is a read-only view — only the ideas in the list decide it")
    void availableTagsIsUnmodifiable() {
        assertThatThrownBy(() -> controller.availableTags().add(Tag.of("invented")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("setQuery writes into the properties rather than shadowing them")
    void setQueryStillWritesIntoTheProperties() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        controller.setQuery(IdeaQuery.all()
                .withTitleContains("portfolio")
                .withTags(java.util.Set.of(Tag.of("web")))
                .withSortOrder(SortOrder.OLDEST_FIRST));

        assertThat(controller.searchTextProperty().get()).isEqualTo("portfolio");
        assertThat(controller.selectedTags()).containsExactly(Tag.of("web"));
        assertThat(controller.sortOrderProperty().get()).isEqualTo(SortOrder.OLDEST_FIRST);
        assertThat(controller.ideas()).containsExactly(PORTFOLIO);
    }

    // ---- Phase 5: mutating the list while a filter is active ----

    @Test
    @DisplayName("an idea the filter excludes is created but not shown")
    void addingAnIdeaTheFilterExcludesDoesNotShowIt() {
        store(SCHEDULER);
        controller.load();
        controller.selectedTags().add(Tag.of("java"));

        controller.add(PORTFOLIO);

        assertThat(controller.ideas()).containsExactly(SCHEDULER);
        assertThat(controller.allIdeas()).contains(PORTFOLIO);
    }

    @Test
    @DisplayName("adding an idea registers a tag nobody was using yet")
    void addingAnIdeaRegistersItsNewTag() {
        store(SCHEDULER);
        controller.load();

        controller.add(NOTES);

        assertThat(controller.availableTags()).containsExactly(Tag.of("java"), Tag.of("school"));
    }

    @Test
    @DisplayName("an edit that drops the last use of a tag drops its chip too")
    void replaceUpdatesAvailableTagsWhenTagsChange() {
        store(SCHEDULER, PORTFOLIO);
        controller.load();

        controller.replace(PORTFOLIO.withTags(IdeaFixtures.tags("school")));

        assertThat(controller.availableTags()).containsExactly(Tag.of("java"), Tag.of("school"));
    }

    @Test
    @DisplayName("delete reaches an idea the filter is hiding")
    void deleteRemovesTheIdeaEvenWhenFilteredOut() {
        InMemoryIdeaRepository backing = new InMemoryIdeaRepository();
        backing.save(SCHEDULER);
        backing.save(PORTFOLIO);

        RecordingIdeaRepository recording = new RecordingIdeaRepository(backing);
        IdeaListController writing = new IdeaListController(serviceOn(recording));
        writing.load();
        writing.selectedTags().add(Tag.of("java"));
        assertThat(writing.ideas()).containsExactly(SCHEDULER);

        assertThat(writing.delete(PORTFOLIO)).isTrue();

        assertThat(recording.deleted()).containsExactly(PORTFOLIO.id());
        assertThat(writing.allIdeas()).containsExactly(SCHEDULER);
    }

    @Test
    @DisplayName("toggleTag selects a tag, then deselects the same tag")
    void toggleTagSelectsThenDeselects() {
        store(SCHEDULER, PORTFOLIO, NOTES);
        controller.load();

        controller.toggleTag(Tag.of("web"));
        assertThat(controller.selectedTags()).containsExactly(Tag.of("web"));
        assertThat(controller.ideas()).containsExactly(PORTFOLIO);

        controller.toggleTag(Tag.of("web"));
        assertThat(controller.selectedTags()).isEmpty();
        assertThat(controller.ideas()).hasSize(3);
    }

    @Test
    @DisplayName("the filter setters reject null")
    void filterSettersRejectNull() {
        assertThatThrownBy(() -> controller.toggleTag(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> controller.setQuery(null))
                .isInstanceOf(NullPointerException.class);
    }

    /** The titles currently on screen, in order. */
    private java.util.List<String> titles() {
        return controller.ideas().stream().map(Idea::title).toList();
    }
}
