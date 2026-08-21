package com.emgi.timeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.controller.IdeaEditorController.SaveResult;
import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
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
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
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
        assertThat(controller.blocks()).hasSize(1);
        assertThat(controller.blocks().get(0).kind()).isEqualTo(BlockKind.TEXT);
        assertThat(controller.blocks().get(0).isBlank()).isTrue();
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
        controller.blocks().get(0).textProperty()
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
        assertThat(controller.blocks()).hasSize(1);
        assertThat(controller.blocks().get(0).textProperty().get())
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
        controller.blocks().get(0).textProperty().set("New body");
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
    @DisplayName("adding an empty block leaves it clean")
    void addingAnEmptyBlockLeavesItClean() {
        controller.beginEdit(IdeaFixtures.ideaTitled("An idea"));

        controller.addBlock(BlockKind.TEXT);
        controller.addBlock(BlockKind.LINK);

        assertThat(controller.isDirty()).isFalse();
    }

    @Test
    @DisplayName("typing into a new block makes it dirty")
    void typingIntoANewBlockMakesItDirty() {
        controller.beginCreate();

        firstBlock().textProperty().set("Body");

        assertThat(controller.isDirty()).isTrue();
    }

    @Test
    @DisplayName("removing a block makes it dirty")
    void removingABlockMakesItDirty() {
        controller.beginEdit(IdeaFixtures.anIdea()
                .withDescription(new Description(List.of(
                        new TextBlock("First."),
                        new TextBlock("Second."))))
                .build());

        controller.removeBlock(controller.blocks().get(1));

        assertThat(controller.isDirty()).isTrue();
    }

    @Test
    @DisplayName("moving a block makes it dirty — order is content")
    void movingABlockMakesItDirty() {
        controller.beginEdit(IdeaFixtures.anIdea()
                .withDescription(new Description(List.of(
                        new TextBlock("First."),
                        new TextBlock("Second."))))
                .build());

        controller.moveBlockDown(controller.blocks().get(0));

        assertThat(controller.isDirty()).isTrue();
    }

    @Test
    @DisplayName("editing an idea with link and image blocks starts clean")
    void editingAnIdeaWithLinkAndImageBlocksStartsClean() {
        controller.beginEdit(IdeaFixtures.anIdea()
                .withDescription(new Description(List.of(
                        new TextBlock("First."),
                        new LinkBlock(URI.create("https://example.com/x"), "The write-up"),
                        new ImageBlock(URI.create("https://example.com/y.png"), "A diagram"))))
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
        firstBlock().textProperty().set("Body");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);
        assertThat(controller.isDirty()).isTrue();
    }

    private BlockDraft firstBlock() {
        return controller.blocks().get(0);
    }

    private BlockDraft addLink(String uri, String label) {
        BlockDraft draft = controller.addBlock(BlockKind.LINK);
        draft.uriProperty().set(uri);
        draft.labelProperty().set(label);
        return draft;
    }

    private BlockDraft addImage(String uri, String altText) {
        BlockDraft draft = controller.addBlock(BlockKind.IMAGE);
        draft.uriProperty().set(uri);
        draft.altTextProperty().set(altText);
        return draft;
    }

    @Test
    @DisplayName("beginEdit lays the stored blocks out in the order they were stored")
    void beginEditKeepsBlockOrder() {
        Idea stored = IdeaFixtures.anIdea()
                .withDescription(new Description(List.of(
                        new TextBlock("First."),
                        LinkBlock.of(URI.create("https://example.com/x")),
                        new ImageBlock(URI.create("file:///C:/pics/a.png"), "A screenshot"))))
                .build();

        controller.beginEdit(stored);

        assertThat(controller.blocks()).hasSize(3);
        assertThat(controller.blocks().get(0).kind()).isEqualTo(BlockKind.TEXT);
        assertThat(controller.blocks().get(1).kind()).isEqualTo(BlockKind.LINK);
        assertThat(controller.blocks().get(2).kind()).isEqualTo(BlockKind.IMAGE);
    }

    @Test
    @DisplayName("an idea saved with no description still opens on a row you can type into")
    void beginEditSeedsARowForAnEmptyDescription() {
        controller.beginEdit(IdeaFixtures.anIdea().withDescription(Description.empty()).build());

        assertThat(controller.blocks()).hasSize(1);
        assertThat(controller.blocks().get(0).kind()).isEqualTo(BlockKind.TEXT);
        assertThat(controller.blocks().get(0).isBlank()).isTrue();
    }

    @Test
    @DisplayName("beginEdit twice does not accumulate rows")
    void beginEditTwiceDoesNotAccumulate() {
        Idea stored = IdeaFixtures.anIdea().withText("Body").build();

        controller.beginEdit(stored);
        controller.beginEdit(stored);

        assertThat(controller.blocks()).hasSize(1);
    }

    @Test
    @DisplayName("beginEdit copies a link's label, so an unnamed link keeps showing its URL")
    void beginEditCopiesTheLinkLabel() {
        controller.beginEdit(IdeaFixtures.anIdea()
                .withDescription(new Description(List.of(
                        LinkBlock.of(URI.create("https://example.com/x")))))
                .build());

        assertThat(firstBlock().labelProperty().get()).isEqualTo("https://example.com/x");
    }

    @Test
    @DisplayName("addBlock appends and hands the new row back")
    void addBlockAppends() {
        controller.beginCreate();

        BlockDraft added = controller.addBlock(BlockKind.LINK);

        assertThat(controller.blocks()).hasSize(2);
        assertThat(controller.blocks().get(1)).isSameAs(added);
        assertThat(added.kind()).isEqualTo(BlockKind.LINK);
    }

    @Test
    @DisplayName("addBlock works on a controller that was never begun")
    void addBlockWithoutBegin() {
        BlockDraft added = controller.addBlock(BlockKind.TEXT);

        assertThat(controller.blocks()).containsExactly(added);
    }

    @Test
    @DisplayName("removeBlock drops that row and leaves the rest in order")
    void removeBlockDropsOneRow() {
        controller.beginCreate();
        BlockDraft second = controller.addBlock(BlockKind.LINK);
        BlockDraft third = controller.addBlock(BlockKind.IMAGE);

        controller.removeBlock(second);

        assertThat(controller.blocks()).hasSize(2);
        assertThat(controller.blocks().get(1)).isSameAs(third);
    }

    @Test
    @DisplayName("removing the only row is allowed — an idea may have no description")
    void removingTheOnlyRowIsAllowed() {
        controller.beginCreate();

        controller.removeBlock(firstBlock());

        assertThat(controller.blocks()).isEmpty();
    }

    @Test
    @DisplayName("moving the first row up does nothing")
    void movingTheFirstRowUpDoesNothing() {
        controller.beginCreate();
        BlockDraft second = controller.addBlock(BlockKind.LINK);
        BlockDraft first = firstBlock();

        controller.moveBlockUp(first);

        assertThat(controller.blocks()).containsExactly(first, second);
    }

    @Test
    @DisplayName("moving the last row down does nothing")
    void movingTheLastRowDownDoesNothing() {
        controller.beginCreate();
        BlockDraft first = firstBlock();
        BlockDraft second = controller.addBlock(BlockKind.LINK);

        controller.moveBlockDown(second);

        assertThat(controller.blocks()).containsExactly(first, second);
    }

    @Test
    @DisplayName("up then down puts a row back where it started")
    void upThenDownRestoresTheOrder() {
        controller.beginCreate();
        BlockDraft first = firstBlock();
        BlockDraft second = controller.addBlock(BlockKind.LINK);
        BlockDraft third = controller.addBlock(BlockKind.IMAGE);

        controller.moveBlockUp(third);
        controller.moveBlockDown(third);

        assertThat(controller.blocks()).containsExactly(first, second, third);
    }

    @Test
    @DisplayName("a text row saves one TextBlock, stripped")
    void textRowSavesOneStrippedTextBlock() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        firstBlock().textProperty().set("  A cleaner approach.  ");

        controller.save();

        assertThat(savedIdea().description().blocks())
                .containsExactly(new TextBlock("A cleaner approach."));
    }

    @Test
    @DisplayName("text, link and image save as three blocks in row order")
    void allThreeKindsSaveInRowOrder() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        firstBlock().textProperty().set("Body");
        addLink("https://example.com/x", "The write-up");
        addImage("file:///C:/pics/a.png", "A screenshot");

        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);

        assertThat(savedIdea().description().blocks()).containsExactly(
                new TextBlock("Body"),
                new LinkBlock(URI.create("https://example.com/x"), "The write-up"),
                new ImageBlock(URI.create("file:///C:/pics/a.png"), "A screenshot"));
    }

    @Test
    @DisplayName("a link with no label saves showing its own URL — LinkBlock's rule, not a copy of it")
    void linkWithNoLabelSavesShowingItsUrl() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        addLink("https://example.com/x", "");

        controller.save();

        assertThat(savedIdea().description().blocks())
                .containsExactly(LinkBlock.of(URI.create("https://example.com/x")));
    }

    @Test
    @DisplayName("an image row carries its alt text through to storage")
    void imageRowCarriesAltText() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        addImage("file:///C:/pics/a.png", "A screenshot");

        controller.save();

        assertThat(savedIdea().description().blocks())
                .containsExactly(new ImageBlock(URI.create("file:///C:/pics/a.png"), "A screenshot"));
    }

    @Test
    @DisplayName("every row blank saves an empty description, not a description of empty blocks")
    void everyRowBlankSavesAnEmptyDescription() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.addBlock(BlockKind.TEXT);
        firstBlock().textProperty().set("   \n\t ");

        controller.save();

        assertThat(savedIdea().description().isEmpty()).isTrue();
        assertThat(savedIdea().description().blocks()).isEmpty();
    }

    @Test
    @DisplayName("open an idea, save it unchanged, and the block list comes back identical")
    void editAndSaveRoundTripsTheBlocks() {
        Description original = new Description(List.of(
                new TextBlock("Body"),
                new LinkBlock(URI.create("https://example.com/x"), "The write-up"),
                new ImageBlock(URI.create("file:///C:/pics/a.png"), "A screenshot")));
        Idea stored = store(IdeaFixtures.anIdea().withDescription(original).build());

        controller.beginEdit(stored);
        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);

        assertThat(savedIdea().description()).isEqualTo(original);
    }

    @Test
    @DisplayName("a blank row between two filled ones is dropped, and the survivors keep their order")
    void aBlankRowBetweenTwoFilledOnesIsDropped() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        firstBlock().textProperty().set("First");
        controller.addBlock(BlockKind.TEXT);
        BlockDraft third = controller.addBlock(BlockKind.TEXT);
        third.textProperty().set("Third");

        controller.save();

        assertThat(savedIdea().description().blocks())
                .containsExactly(new TextBlock("First"), new TextBlock("Third"));
    }

    @Test
    @DisplayName("the prune is visible on screen: after a save the blank row is gone from blocks()")
    void thePruneIsVisibleOnScreen() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        firstBlock().textProperty().set("First");
        controller.addBlock(BlockKind.TEXT);

        controller.save();

        assertThat(controller.blocks()).hasSize(1);
    }

    @Test
    @DisplayName("a link row with a label but no address is refused, and nothing reaches the service")
    void aLinkRowWithNoAddressIsRefused() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        addLink("", "The write-up");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        assertThat(controller.descriptionErrorProperty().get())
                .contains("link address is required");
        assertThat(repository.saved()).isEmpty();
    }

    @Test
    @DisplayName("the row number in a message counts surviving rows, so it matches what is on screen")
    void theRowNumberMatchesTheScreen() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        controller.addBlock(BlockKind.TEXT);
        firstBlock().textProperty().set("First");
        addImage("", "A screenshot");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        assertThat(controller.descriptionErrorProperty().get()).startsWith("Block 2:");
    }

    @Test
    @DisplayName("an address that is not a URI at all is caught here, not thrown")
    void anUnparseableAddressIsReportedNotThrown() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        addLink("ht tp://x", "The write-up");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        assertThat(controller.descriptionErrorProperty().get())
                .contains("is not a valid address");
        assertThat(repository.saved()).isEmpty();
    }

    @Test
    @DisplayName("a parseable but relative address reaches the validator — the two error sources stay separate")
    void aRelativeAddressIsTheValidatorsToRefuse() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        addLink("example.com/x", "The write-up");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        assertThat(controller.descriptionErrorProperty().get())
                .contains("must be absolute, including https:// or file://");
        assertThat(controller.descriptionErrorProperty().get())
                .doesNotContain("is not a valid address");
    }

    @Test
    @DisplayName("a refused save consumes nothing — the rows are all still there to fix")
    void aRefusedSaveConsumesNothing() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        firstBlock().textProperty().set("Body");
        addLink("ht tp://x", "The write-up");

        controller.save();

        assertThat(controller.blocks()).hasSize(2);
        assertThat(controller.blocks().get(0).textProperty().get()).isEqualTo("Body");
        assertThat(controller.blocks().get(1).uriProperty().get()).isEqualTo("ht tp://x");
    }

    @Test
    @DisplayName("a blank title and a bad address report on both fields at once")
    void bothFieldsReportTogether() {
        controller.beginCreate();
        addLink("example.com/x", "The write-up");

        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        assertThat(controller.titleErrorProperty().get()).isNotEmpty();
        assertThat(controller.descriptionErrorProperty().get()).isNotEmpty();
    }

    @Test
    @DisplayName("a successful retry clears the previous description message")
    void aSuccessfulRetryClearsTheDescriptionMessage() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        BlockDraft link = addLink("ht tp://x", "The write-up");
        assertThat(controller.save()).isEqualTo(SaveResult.INVALID);

        link.uriProperty().set("https://example.com/x");

        assertThat(controller.save()).isEqualTo(SaveResult.SAVED);
        assertThat(controller.descriptionErrorProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("beginCreate after a failed save clears the description message")
    void beginCreateClearsTheDescriptionMessage() {
        controller.beginCreate();
        controller.titleProperty().set("An idea");
        addLink("ht tp://x", "The write-up");
        controller.save();

        controller.beginCreate();

        assertThat(controller.descriptionErrorProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("addBlock, removeBlock and the two moves reject null")
    void blockOperationsRejectNull() {
        assertThatThrownBy(() -> controller.addBlock(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> controller.removeBlock(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> controller.moveBlockUp(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> controller.moveBlockDown(null)).isInstanceOf(NullPointerException.class);
    }
}
