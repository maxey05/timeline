package com.emgi.timeline.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.emgi.timeline.support.IdeaFixtures.tags;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.support.IdeaFixtures;
import java.util.LinkedHashSet;
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
    void acceptsADescriptionAtTheLengthLimit() {
        Description atLimit =
                Description.ofText("x".repeat(IdeaValidator.DESCRIPTION_MAX_LENGTH));

        assertThat(validator.validate(command("Fine", atLimit, Set.of())).isValid()).isTrue();
    }

    @Test
    void rejectsADescriptionOverTheLengthLimit() {
        Description tooLong =
                Description.ofText("x".repeat(IdeaValidator.DESCRIPTION_MAX_LENGTH + 1));

        ValidationResult result = validator.validate(command("Fine", tooLong, Set.of()));

        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION)).hasSize(1);
    }

    @Test
    @DisplayName("an image address without a scheme is rejected — nothing could load it")
    void rejectsRelativeImageSource() {
        Description description = Description.ofText("![cat](pictures/cat.png)");

        ValidationResult result = validator.validate(command("Fine", description, Set.of()));

        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION)).hasSize(1);
        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION).get(0)).contains("Image 1");
    }

    @Test
    void acceptsAbsoluteHttpAndFileImageSources() {
        Description description = Description.ofText(
                "notes about https://example.com/page\n"
                        + "![cat](file:///home/user/cat.png)\n"
                        + "![logo](https://example.com/logo.png)");

        assertThat(validator.validate(command("Fine", description, Set.of())).isValid()).isTrue();
    }

    @Test
    @DisplayName("the offending image is identified by its 1-based position among the images")
    void reportsTheImagePosition() {
        Description description = Description.ofText(
                "![ok](https://example.com/a.png)\n"
                        + "words in between\n"
                        + "![bad](pictures/cat.png)");

        ValidationResult result = validator.validate(command("Fine", description, Set.of()));

        assertThat(result.messagesFor(IdeaValidator.FIELD_DESCRIPTION).get(0)).contains("Image 2");
    }

    @Test
    @DisplayName("malformed link and image syntax is text, not a validation error")
    void malformedSyntaxIsNotAnError() {
        Description description = Description.ofText("![ (unclosed and https://exa[mple");

        assertThat(validator.validate(command("Fine", description, Set.of())).isValid()).isTrue();
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
        Description description = Description.ofText("![bad](pictures/cat.png)");

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
