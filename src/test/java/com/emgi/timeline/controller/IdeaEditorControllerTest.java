package com.emgi.timeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.controller.IdeaEditorController.SaveResult;
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

    private Idea store(Idea idea) {
        repository.save(idea);
        return idea;
    }

    private Idea savedIdea() {
        return controller.savedIdea().orElseThrow();
    }

    @Test
    @DisplayName("a new form starts empty, incomplete, and error-free")
    void newFormStartsEmpty() {
        controller.beginCreate();

        assertThat(controller.isEditing()).isFalse();
        assertThat(controller.titleProperty().get()).isEmpty();
        assertThat(controller.descriptionProperty().get()).isEmpty();
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
        controller.descriptionProperty()
                .set("x".repeat(IdeaValidator.DESCRIPTION_MAX_LENGTH + 1));

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

        assertThat(controller.tags()).hasSize(IdeaValidator.MAX_TAGS + 1);
        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);
        assertThat(controller.tagsErrorProperty().get())
                .contains(String.valueOf(IdeaValidator.MAX_TAGS));
        assertThat(repository.saved()).isEmpty();
    }

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
        assertThat(controller.descriptionProperty().get())
                .isEqualTo("Static, no framework.");
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
    @DisplayName("editing the form does not touch the stored idea — cancel is free")
    void editingDoesNotMutateTheStoredIdea() {
        Idea stored = store(IdeaFixtures.anIdea().withTitle("Original").withTags("java").build());

        controller.beginEdit(stored);
        controller.titleProperty().set("Changed my mind");
        controller.descriptionProperty().set("New body");
        controller.statusProperty().set(IdeaStatus.COMPLETED);
        controller.addTag("web");

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


    @Test
    @DisplayName("a fresh create form is not dirty")
    void aFreshCreateFormIsNotDirty() {
        controller.beginCreate();

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("a freshly opened edit form is not dirty")
    void aFreshlyOpenedEditFormIsNotDirty() {
        controller.beginEdit(IdeaFixtures.anIdea()
                .withTitle("Rewrite the scheduler")
                .withDescription(Description.ofText("A cleaner approach."))
                .withTags(IdeaFixtures.tags("java", "school"))
                .withStatus(IdeaStatus.IN_PROGRESS)
                .build());

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("typing in the title makes it dirty")
    void typingInTheTitleMakesItDirty() {
        controller.beginCreate();

        controller.titleProperty().set("An idea");

        assertThat(controller.isDirty()).isTrue();
    }

    @Test
    @DisplayName("typing and undoing leaves it clean")
    void typingAndUndoingLeavesItClean() {
        controller.beginCreate();

        controller.titleProperty().set("An idea");
        controller.titleProperty().set("");

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("trailing whitespace in the title is not a dirtying change")
    void trailingWhitespaceInTheTitleIsNotADirtyingChange() {
        controller.beginEdit(IdeaFixtures.ideaTitled("An idea"));

        controller.titleProperty().set("An idea   ");

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("changing the status makes it dirty")
    void changingTheStatusMakesItDirty() {
        controller.beginCreate();

        controller.statusProperty().set(IdeaStatus.COMPLETED);

        assertThat(controller.isDirty()).isTrue();
    }

    @Test
    @DisplayName("adding a tag makes it dirty")
    void addingATagMakesItDirty() {
        controller.beginCreate();

        controller.addTag("java");

        assertThat(controller.isDirty()).isTrue();
    }

    @Test
    @DisplayName("removing a tag makes it dirty")
    void removingATagMakesItDirty() {
        controller.beginEdit(IdeaFixtures.anIdea().withTags(IdeaFixtures.tags("java")).build());

        controller.removeTag(Tag.of("java"));

        assertThat(controller.isDirty()).isTrue();
    }

    @Test
    @DisplayName("re-adding a tag that is already there leaves it clean")
    void addingATagThatIsAlreadyThereLeavesItClean() {
        controller.beginEdit(IdeaFixtures.anIdea().withTags(IdeaFixtures.tags("java")).build());

        controller.addTag("JAVA");

        assertThat(controller.tags()).hasSize(1);
        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("removing a tag and adding it back leaves it clean, whatever the order")
    void removingATagAndAddingItBackLeavesItClean() {
        controller.beginEdit(IdeaFixtures.anIdea()
                .withTags(IdeaFixtures.tags("java", "school"))
                .build());

        controller.removeTag(Tag.of("java"));
        controller.addTag("java");

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("typing in the description makes it dirty")
    void typingInTheDescriptionMakesItDirty() {
        controller.beginCreate();

        controller.descriptionProperty().set("Body");

        assertThat(controller.isDirty()).isTrue();
    }

    @Test
    @DisplayName("typing in the description and undoing leaves it clean")
    void typingInTheDescriptionAndUndoingLeavesItClean() {
        controller.beginEdit(IdeaFixtures.anIdea().withText("Body").build());

        controller.descriptionProperty().set("Body and more");
        controller.descriptionProperty().set("Body");

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("trailing whitespace in the description is not a dirtying change")
    void trailingWhitespaceInTheDescriptionIsNotADirtyingChange() {
        controller.beginEdit(IdeaFixtures.anIdea().withText("Body").build());

        controller.descriptionProperty().set("Body\n\n  ");

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("opening an idea whose description holds a link and an image starts clean")
    void editingAnIdeaWithLinksAndImagesStartsClean() {
        controller.beginEdit(IdeaFixtures.anIdea()
                .withText("First.\n"
                        + "See https://example.com/x for the write-up.\n"
                        + "![A diagram](https://example.com/y.png)")
                .build());

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("beginCreate after a dirty edit resets the baseline")
    void beginCreateResetsTheBaseline() {
        controller.beginEdit(IdeaFixtures.ideaTitled("An idea"));
        controller.titleProperty().set("Something else");

        controller.beginCreate();

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("a failed save leaves the form dirty, so Esc still asks")
    void aFailedSaveLeavesTheFormDirty() {
        controller.beginCreate();
        controller.titleProperty().set("   ");
        controller.descriptionProperty().set("Body");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);
        assertThat(controller.isDirty()).isTrue();
    }

    // -------------------------------------------------------------- the description box

    @Test
    @DisplayName("beginEdit loads the stored text verbatim, newlines and tokens included")
    void beginEditLoadsTheDescriptionVerbatim() {
        String body = "First.\n\n"
                + "See https://example.com/x for the write-up.\n"
                + "![A screenshot](file:///C:/pics/a.png)";

        controller.beginEdit(IdeaFixtures.anIdea().withText(body).build());

        assertThat(controller.descriptionProperty().get()).isEqualTo(body);
    }

    @Test
    @DisplayName("an idea saved with no description opens on an empty box, not a null one")
    void beginEditOnAnEmptyDescriptionGivesAnEmptyBox() {
        controller.beginEdit(IdeaFixtures.anIdea().withDescription(Description.empty()).build());

        assertThat(controller.descriptionProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("beginEdit twice does not accumulate text")
    void beginEditTwiceDoesNotAccumulate() {
        Idea stored = IdeaFixtures.anIdea().withText("Body").build();

        controller.beginEdit(stored);
        controller.beginEdit(stored);

        assertThat(controller.descriptionProperty().get()).isEqualTo("Body");
    }

    @Test
    @DisplayName("save strips the edges of the description but nothing inside it")
    void saveStripsOnlyTheEdges() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.descriptionProperty().set("  A cleaner approach.\n\n  With a second part. \n ");

        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);

        assertThat(savedIdea().description().text())
                .isEqualTo("A cleaner approach.\n\n  With a second part.");
    }

    @Test
    @DisplayName("a description of nothing but whitespace saves as empty")
    void whitespaceOnlyDescriptionSavesAsEmpty() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.descriptionProperty().set("   \n\t ");

        controller.save();

        assertThat(savedIdea().description().isEmpty()).isTrue();
        assertThat(savedIdea().description().text()).isEmpty();
    }

    @Test
    @DisplayName("link and image syntax reaches storage as the text the user typed")
    void tokensReachStorageUntouched() {
        String body = "Body\n"
                + "https://example.com/x\n"
                + "![A screenshot](file:///C:/pics/a.png)";
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.descriptionProperty().set(body);

        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);

        assertThat(savedIdea().description().text()).isEqualTo(body);
    }

    @Test
    @DisplayName("open an idea, save it unchanged, and the description comes back identical")
    void editAndSaveRoundTripsTheDescription() {
        Description original = Description.ofText(
                "Body\nhttps://example.com/x\n![A screenshot](file:///C:/pics/a.png)");
        Idea stored = store(IdeaFixtures.anIdea().withDescription(original).build());

        controller.beginEdit(stored);
        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);

        assertThat(savedIdea().description()).isEqualTo(original);
    }

    @Test
    @DisplayName("an image address with no scheme is refused, and nothing reaches the service")
    void aRelativeImageAddressIsRefused() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.descriptionProperty().set("![A screenshot](pics/a.png)");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        assertThat(controller.descriptionErrorProperty().get()).contains("Image 1");
        assertThat(repository.saved()).isEmpty();
    }

    @Test
    @DisplayName("half-typed link and image syntax is not an error — it is just text")
    void malformedSyntaxSavesAsText() {
        String body = "![ half a token and https://exa[mple";
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.descriptionProperty().set(body);

        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);

        assertThat(savedIdea().description().text()).isEqualTo(body);
    }
}
