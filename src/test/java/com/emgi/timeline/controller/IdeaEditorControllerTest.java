package com.emgi.timeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.controller.IdeaEditorController.SaveResult;
import com.emgi.timeline.domain.content.TextBlock;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.domain.validation.IdeaValidator;
import com.emgi.timeline.service.IdeaService;
import com.emgi.timeline.support.FixedClock;
import com.emgi.timeline.support.IdeaFixtures;
import com.emgi.timeline.support.RecordingIdeaRepository;
import com.emgi.timeline.support.SequentialIdGenerator;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The §7.1–7.2 form logic, tested with no toolkit running — the payoff of the rule
 * {@link ControllerPurityTest} enforces.
 *
 * <p>Several of these assert not just the outcome but that <em>nothing was written</em>, which is
 * §8's "invalid input never reaches the repository, use a spy to prove it" applied one layer up
 * from {@code IdeaServiceTest}.
 */
@DisplayName("IdeaEditorController")
class IdeaEditorControllerTest {

    private RecordingIdeaRepository repository;
    private FixedClock clock;
    private IdeaEditorController controller;

    @BeforeEach
    void setUp() {
        repository = new RecordingIdeaRepository();
        clock = FixedClock.atDefault();
        controller = new IdeaEditorController(
                new IdeaService(repository, new IdeaValidator(), new SequentialIdGenerator(), clock));
    }

    /** Puts an idea in storage without going through the form. */
    private Idea store(Idea idea) {
        repository.save(idea);
        return idea;
    }

    private Idea savedIdea() {
        return controller.savedIdea().orElseThrow();
    }

    // ---- create mode ---------------------------------------------------------------

    @Test
    @DisplayName("a new form starts empty, incomplete, and error-free")
    void newFormStartsEmpty() {
        controller.beginCreate();

        assertThat(controller.isEditing()).isFalse();
        assertThat(controller.titleProperty().get()).isEmpty();
        assertThat(controller.descriptionTextProperty().get()).isEmpty();
        assertThat(controller.statusProperty().get()).isEqualTo(IdeaStatus.INCOMPLETE);
        assertThat(controller.tags()).isEmpty();
        assertThat(controller.titleErrorProperty().get()).isEmpty();
        assertThat(controller.descriptionErrorProperty().get()).isEmpty();
        assertThat(controller.tagsErrorProperty().get()).isEmpty();
        assertThat(controller.savedIdea()).isEmpty();
    }

    @Test
    @DisplayName("saving a valid form persists it exactly once")
    void savingAValidFormPersistsItOnce() {
        controller.beginCreate();
        controller.titleProperty().set("Rewrite the scheduler");

        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);

        assertThat(repository.saved()).hasSize(1);
        assertThat(controller.savedIdea()).isPresent();
    }

    @Test
    @DisplayName("the saved idea carries the form's fields, with timestamps from the clock")
    void savedIdeaCarriesTheFormsFields() {
        controller.beginCreate();
        controller.titleProperty().set("Toy database engine");
        controller.statusProperty().set(IdeaStatus.IN_PROGRESS);
        controller.addTag("java");
        controller.addTag("school");

        controller.save();

        Idea saved = savedIdea();
        assertThat(saved.title()).isEqualTo("Toy database engine");
        assertThat(saved.status()).isEqualTo(IdeaStatus.IN_PROGRESS);
        assertThat(saved.tags()).containsExactlyInAnyOrder(Tag.of("java"), Tag.of("school"));
        assertThat(saved.createdAt()).isEqualTo(FixedClock.DEFAULT_INSTANT);
        assertThat(saved.updatedAt()).isEqualTo(FixedClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("the description becomes exactly one text block")
    void descriptionBecomesOneTextBlock() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.descriptionTextProperty().set("A cleaner approach to the priority queue.");

        controller.save();

        assertThat(savedIdea().description().blocks())
                .containsExactly(new TextBlock("A cleaner approach to the priority queue."));
    }

    @Test
    @DisplayName("a blank description becomes an empty description, not one empty block")
    void blankDescriptionBecomesAnEmptyDescription() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.descriptionTextProperty().set("   \n\t ");

        controller.save();

        assertThat(savedIdea().description().isEmpty()).isTrue();
        assertThat(savedIdea().description().blocks()).isEmpty();
    }

    @Test
    @DisplayName("the title is stripped by the service, so the rule stays in one place")
    void titleWhitespaceIsStrippedByTheService() {
        controller.beginCreate();
        controller.titleProperty().set("   Portfolio site ideas   ");

        controller.save();

        assertThat(savedIdea().title()).isEqualTo("Portfolio site ideas");
    }

    @Test
    @DisplayName("a blank title is rejected and reported on the title field")
    void blankTitleIsRejected() {
        controller.beginCreate();
        controller.titleProperty().set("   ");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        assertThat(controller.titleErrorProperty().get()).isEqualTo("Title is required.");
        assertThat(controller.savedIdea()).isEmpty();
    }

    @Test
    @DisplayName("an invalid form writes nothing to the repository")
    void invalidFormWritesNothing() {
        controller.beginCreate();
        controller.titleProperty().set("");

        controller.save();

        assertThat(repository.saved()).isEmpty();
    }

    @Test
    @DisplayName("errors clear on a successful retry")
    void errorsClearOnASuccessfulRetry() {
        controller.beginCreate();
        controller.save();
        assertThat(controller.titleErrorProperty().get()).isNotEmpty();

        controller.titleProperty().set("Now it has a title");

        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);
        assertThat(controller.titleErrorProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("an over-long title is rejected")
    void anOverlongTitleIsRejected() {
        controller.beginCreate();
        controller.titleProperty().set("x".repeat(IdeaValidator.TITLE_MAX_LENGTH + 1));

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);
        assertThat(controller.titleErrorProperty().get())
                .contains(String.valueOf(IdeaValidator.TITLE_MAX_LENGTH));
    }

    @Test
    @DisplayName("an over-long description lands on the description field, not the title one")
    void anOverlongDescriptionIsReported() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.descriptionTextProperty()
                .set("x".repeat(IdeaValidator.TEXT_BLOCK_MAX_LENGTH + 1));

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        assertThat(controller.descriptionErrorProperty().get()).isNotEmpty();
        assertThat(controller.titleErrorProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("more than twenty tags is refused by the validator, on the tags field")
    void moreThanTwentyTagsIsRejectedOnSave() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        for (int i = 0; i <= IdeaValidator.MAX_TAGS; i++) {
            controller.addTag("tag" + i);
        }

        // The form deliberately does not enforce the cap; the validator owns that rule.
        assertThat(controller.tags()).hasSize(IdeaValidator.MAX_TAGS + 1);
        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);
        assertThat(controller.tagsErrorProperty().get())
                .contains(String.valueOf(IdeaValidator.MAX_TAGS));
        assertThat(repository.saved()).isEmpty();
    }

    // ---- edit mode -----------------------------------------------------------------

    @Test
    @DisplayName("beginEdit populates every field from the stored idea")
    void beginEditPopulatesTheForm() {
        Idea stored = store(IdeaFixtures.anIdea()
                .withTitle("Portfolio site ideas")
                .withText("Static, no framework.")
                .withTags("web")
                .withStatus(IdeaStatus.IN_PROGRESS)
                .build());

        controller.beginEdit(stored);

        assertThat(controller.isEditing()).isTrue();
        assertThat(controller.titleProperty().get()).isEqualTo("Portfolio site ideas");
        assertThat(controller.descriptionTextProperty().get()).isEqualTo("Static, no framework.");
        assertThat(controller.statusProperty().get()).isEqualTo(IdeaStatus.IN_PROGRESS);
        assertThat(controller.tags()).containsExactly(Tag.of("web"));
    }

    @Test
    @DisplayName("beginEdit puts the chips in a stable order, whatever the set's iteration order")
    void beginEditPutsTagsInAStableOrder() {
        Set<Tag> unordered = new LinkedHashSet<>();
        unordered.add(Tag.of("web"));
        unordered.add(Tag.of("java"));
        unordered.add(Tag.of("school"));

        controller.beginEdit(IdeaFixtures.anIdea().withTags(unordered).build());

        assertThat(controller.tags())
                .containsExactly(Tag.of("java"), Tag.of("school"), Tag.of("web"));
    }

    @Test
    @DisplayName("several text blocks flatten into the one text area, separated by a blank line")
    void beginEditFlattensSeveralTextBlocks() {
        Idea stored = IdeaFixtures.anIdea()
                .withDescription(new Description(
                        java.util.List.of(new TextBlock("First."), new TextBlock("Second."))))
                .build();

        controller.beginEdit(stored);

        assertThat(controller.descriptionTextProperty().get()).isEqualTo("First.\n\nSecond.");
    }

    @Test
    @DisplayName("editing the form does not touch the stored idea — cancel is free")
    void editingDoesNotMutateTheStoredIdea() {
        Idea stored = store(IdeaFixtures.anIdea().withTitle("Original").withTags("java").build());

        controller.beginEdit(stored);
        controller.titleProperty().set("Changed my mind");
        controller.descriptionTextProperty().set("New body");
        controller.statusProperty().set(IdeaStatus.COMPLETED);
        controller.addTag("web");
        // No save: the dialog was cancelled.

        Idea reloaded = repository.findById(stored.id()).orElseThrow();
        assertThat(reloaded).isEqualTo(stored);
        assertThat(reloaded.title()).isEqualTo("Original");
    }

    @Test
    @DisplayName("saving an edit preserves createdAt and bumps updatedAt")
    void savingAnEditPreservesCreatedAtAndBumpsUpdatedAt() {
        Idea stored = store(IdeaFixtures.anIdea().withTitle("Original").build());

        controller.beginEdit(stored);
        controller.titleProperty().set("Edited");
        clock.advance(Duration.ofMinutes(5));

        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);

        assertThat(savedIdea().createdAt()).isEqualTo(stored.createdAt());
        assertThat(savedIdea().updatedAt())
                .isEqualTo(FixedClock.DEFAULT_INSTANT.plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("saving an edit keeps the same id")
    void savingAnEditKeepsTheSameId() {
        Idea stored = store(IdeaFixtures.anIdea().withTitle("Original").build());

        controller.beginEdit(stored);
        controller.titleProperty().set("Edited");
        controller.save();

        assertThat(savedIdea().id()).isEqualTo(stored.id());
    }

    @Test
    @DisplayName("editing an idea that was deleted underneath reports MISSING and writes nothing")
    void editingAnIdeaThatWasDeletedReportsMissing() {
        Idea stored = store(IdeaFixtures.anIdea().withTitle("Doomed").build());
        controller.beginEdit(stored);
        controller.titleProperty().set("Edited");

        repository.delete(stored.id());
        int writesBeforeSave = repository.saved().size();

        assertThat(controller.save()).isEqualTo(SaveResult.MISSING);
        assertThat(controller.savedIdea()).isEmpty();
        assertThat(repository.saved()).hasSize(writesBeforeSave);
    }

    @Test
    @DisplayName("a missing target is flagged to the caller, so the stale row can be reloaded")
    void missingTargetIsFlagged() {
        Idea stored = store(IdeaFixtures.anIdea().withTitle("Doomed").build());
        controller.beginEdit(stored);
        repository.delete(stored.id());

        controller.save();
        assertThat(controller.targetMissing()).isTrue();

        // A normal save on a live idea leaves the flag down.
        Idea alive = store(IdeaFixtures.anIdea().withIdNumber(9).withTitle("Alive").build());
        controller.beginEdit(alive);
        controller.save();
        assertThat(controller.targetMissing()).isFalse();
    }

    @Test
    @DisplayName("an invalid edit leaves the stored idea untouched")
    void anInvalidEditLeavesTheStoredIdeaUntouched() {
        Idea stored = store(IdeaFixtures.anIdea().withTitle("Original").build());

        controller.beginEdit(stored);
        controller.titleProperty().set("   ");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);
        assertThat(repository.findById(stored.id()).orElseThrow()).isEqualTo(stored);
    }

    @Test
    @DisplayName("beginCreate after beginEdit clears the form back to create mode")
    void beginCreateResetsEditMode() {
        controller.beginEdit(IdeaFixtures.anIdea().withTitle("Something").withTags("java").build());
        controller.beginCreate();

        assertThat(controller.isEditing()).isFalse();
        assertThat(controller.titleProperty().get()).isEmpty();
        assertThat(controller.tags()).isEmpty();
    }

    // ---- tag entry -----------------------------------------------------------------

    @Test
    @DisplayName("addTag normalizes what the user typed")
    void addTagNormalizes() {
        controller.beginCreate();

        assertThat(controller.addTag("  Java  ")).isTrue();

        assertThat(controller.tags()).containsExactly(Tag.of("java"));
    }

    @Test
    @DisplayName("addTag ignores a duplicate regardless of case, without an error")
    void addTagIgnoresDuplicatesRegardlessOfCase() {
        controller.beginCreate();
        controller.addTag("Java");

        assertThat(controller.addTag("  java ")).isTrue();

        assertThat(controller.tags()).containsExactly(Tag.of("java"));
        assertThat(controller.tagsErrorProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("addTag rejects blank input quietly — pressing Enter on an empty field is not an error")
    void addTagRejectsBlankQuietly() {
        controller.beginCreate();

        assertThat(controller.addTag("   ")).isFalse();
        assertThat(controller.addTag(null)).isFalse();

        assertThat(controller.tags()).isEmpty();
        assertThat(controller.tagsErrorProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("addTag rejects an over-long name and says why")
    void addTagRejectsAnOverlongName() {
        controller.beginCreate();

        assertThat(controller.addTag("x".repeat(Tag.MAX_LENGTH + 1))).isFalse();

        assertThat(controller.tags()).isEmpty();
        assertThat(controller.tagsErrorProperty().get())
                .contains(String.valueOf(Tag.MAX_LENGTH));
    }

    @Test
    @DisplayName("a successful addTag clears a previous tag error")
    void addTagClearsAPreviousError() {
        controller.beginCreate();
        controller.addTag("x".repeat(Tag.MAX_LENGTH + 1));
        assertThat(controller.tagsErrorProperty().get()).isNotEmpty();

        controller.addTag("java");

        assertThat(controller.tagsErrorProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("removeTag drops the chip and clears any tag error")
    void removeTagDropsTheChip() {
        controller.beginCreate();
        controller.addTag("java");
        controller.addTag("web");
        controller.addTag("x".repeat(Tag.MAX_LENGTH + 1));

        controller.removeTag(Tag.of("java"));

        assertThat(controller.tags()).containsExactly(Tag.of("web"));
        assertThat(controller.tagsErrorProperty().get()).isEmpty();
    }

    // ---- construction --------------------------------------------------------------

    @Test
    @DisplayName("a null service is rejected at construction")
    void rejectsNullService() {
        assertThatThrownBy(() -> new IdeaEditorController(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("beginEdit and removeTag reject null")
    void rejectsNullArguments() {
        assertThatThrownBy(() -> controller.beginEdit(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> controller.removeTag(null))
                .isInstanceOf(NullPointerException.class);
    }
}
