package com.emgi.timeline.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.emgi.timeline.support.IdeaFixtures.tags;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
import com.emgi.timeline.domain.content.TextBlock;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.support.IdeaFixtures;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IdeaValidatorTest {

    private final IdeaValidator validator = new IdeaValidator();

    private static CreateIdeaCommand command(String title, Description description, Set<Tag> tagSet) {
        return new CreateIdeaCommand(title, description, tagSet, IdeaStatus.INCOMPLETE);
    }

    @Test
    void acceptsAWellFormedCommand() {
        assertThat(validator.validate(IdeaFixtures.aCreateCommand()).isValid()).isTrue();
    }

    @ParameterizedTest(name = "title = [{0}]")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    @DisplayName("a missing or whitespace-only title is rejected — isBlank, not isEmpty")
    void rejectsBlankTitle(String title) {
        ValidationResult result = validator.validate(CreateIdeaCommand.of(title));

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.messagesFor(IdeaValidator.FIELD_TITLE)).containsExactly("Title is required.");
    }

    @Test
    void acceptsATitleAtTheLengthLimit() {
        String atLimit = "t".repeat(IdeaValidator.TITLE_MAX_LENGTH);

        assertThat(validator.validate(CreateIdeaCommand.of(atLimit)).isValid()).isTrue();
    }

    @Test
    void rejectsATitleOverTheLengthLimit() {
        String overLimit = "t".repeat(IdeaValidator.TITLE_MAX_LENGTH + 1);

        ValidationResult result = validator.validate(CreateIdeaCommand.of(overLimit));

        assertThat(result.messagesFor(IdeaValidator.FIELD_TITLE)).hasSize(1);
        assertThat(result.messagesFor(IdeaValidator.FIELD_TITLE).get(0)).contains("120");
    }

    @Test
    @DisplayName("a blank title reports 'required' only, not 'required' plus 'too long'")
    void blankTitleReportsOneErrorForThatField() {
        ValidationResult result = validator.validate(CreateIdeaCommand.of("   "));

        assertThat(result.messagesFor(IdeaValidator.FIELD_TITLE)).hasSize(1);
    }

    @Test
    void rejectsATextBlockOverTheLengthLimit() {
        Description tooLong = Description.ofText("x".repeat(IdeaValidator.TEXT_BLOCK_MAX_LENGTH + 1));

        ValidationResult result = validator.validate(command("Fine", tooLong, Set.of()));

        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION)).hasSize(1);
    }

    @Test
    @DisplayName("a link without a scheme is rejected — nothing could open it")
    void rejectsRelativeLinkTarget() {
        Description description = new Description(List.of(
                new LinkBlock(URI.create("example.com/page"), "No scheme")));

        ValidationResult result = validator.validate(command("Fine", description, Set.of()));

        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION)).hasSize(1);
        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION).get(0))
                .contains("Block 1").contains("link");
    }

    @Test
    void rejectsRelativeImageSource() {
        Description description = new Description(List.of(
                new ImageBlock(URI.create("pictures/cat.png"), "cat")));

        ValidationResult result = validator.validate(command("Fine", description, Set.of()));

        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION)).hasSize(1);
        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION).get(0)).contains("image");
    }

    @Test
    void acceptsAbsoluteHttpAndFileUris() {
        Description description = new Description(List.of(
                new LinkBlock(URI.create("https://example.com/page"), "ok"),
                new ImageBlock(URI.create("file:///home/user/cat.png"), "cat")));

        assertThat(validator.validate(command("Fine", description, Set.of())).isValid()).isTrue();
    }

    @Test
    @DisplayName("the offending block is identified by its 1-based position")
    void reportsTheBlockPosition() {
        Description description = new Description(List.of(
                new TextBlock("fine"),
                new TextBlock("also fine"),
                new LinkBlock(URI.create("nope"), "bad")));

        ValidationResult result = validator.validate(command("Fine", description, Set.of()));

        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION).get(0)).contains("Block 3");
    }

    @Test
    void rejectsMoreTagsThanTheLimit() {
        Set<Tag> tooMany = new LinkedHashSet<>();
        for (int i = 0; i <= IdeaValidator.MAX_TAGS; i++) {
            tooMany.add(Tag.of("tag" + i));
        }

        ValidationResult result = validator.validate(command("Fine", Description.empty(), tooMany));

        assertThat(result.messagesFor(IdeaValidator.FIELD_TAGS)).hasSize(1);
    }

    @Test
    void acceptsExactlyTheTagLimit() {
        Set<Tag> atLimit = new LinkedHashSet<>();
        for (int i = 0; i < IdeaValidator.MAX_TAGS; i++) {
            atLimit.add(Tag.of("tag" + i));
        }

        assertThat(validator.validate(command("Fine", Description.empty(), atLimit)).isValid()).isTrue();
    }

    @Test
    @DisplayName("every problem is reported at once, not one per round trip")
    void reportsAllErrorsTogether() {
        Description description = new Description(List.of(new LinkBlock(URI.create("nope"), "bad")));

        ValidationResult result = validator.validate(command("  ", description, tags("java")));

        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).extracting(ValidationError::field)
                .containsExactlyInAnyOrder(IdeaValidator.FIELD_TITLE, IdeaValidator.FIELD_DESCRIPTION);
    }

    @Test
    void validatesUpdateCommandsByTheSameRules() {
        UpdateIdeaCommand invalid = new UpdateIdeaCommand(
                IdeaFixtures.anIdea().build().id(), "  ", Description.empty(), Set.of(),
                IdeaStatus.INCOMPLETE);

        assertThat(validator.validate(invalid).isInvalid()).isTrue();
        assertThat(validator.validate(IdeaFixtures.anUpdateCommand()).isValid()).isTrue();
    }

    @Test
    @DisplayName("a null id on an update fails loudly at construction, not as a form error")
    void updateCommandRejectsNullIdAtConstruction() {
        assertThatThrownBy(() -> new UpdateIdeaCommand(
                null, "Fine", Description.empty(), Set.of(), IdeaStatus.INCOMPLETE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a valid result carries no errors and messagesFor returns empty, not null")
    void validResultIsEmpty() {
        ValidationResult result = ValidationResult.valid();

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.messagesFor(IdeaValidator.FIELD_TITLE)).isEmpty();
    }
}
