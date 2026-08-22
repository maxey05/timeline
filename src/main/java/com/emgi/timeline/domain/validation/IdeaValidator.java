package com.emgi.timeline.domain.validation;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.content.DescriptionParser;
import com.emgi.timeline.domain.content.ImageSegment;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IdeaValidator {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_TAGS = "tags";

    public static final int TITLE_MAX_LENGTH = 120;

    public static final int DESCRIPTION_MAX_LENGTH = 20_000;

    public static final int MAX_TAGS = 20;

    public ValidationResult validate(CreateIdeaCommand command) {
        Objects.requireNonNull(command, "command");
        return validateFields(command.title(), command.description(), command.tags());
    }

    public ValidationResult validate(UpdateIdeaCommand command) {
        Objects.requireNonNull(command, "command");
        return validateFields(command.title(), command.description(), command.tags());
    }

    private ValidationResult validateFields(String title, Description description, Set<Tag> tags) {
        List<ValidationError> errors = new ArrayList<>();
        validateTitle(title, errors);
        validateDescription(description, errors);
        validateTags(tags, errors);
        return ValidationResult.of(errors);
    }

    private void validateTitle(String title, List<ValidationError> errors) {
        if (title == null || title.isBlank()) {
            errors.add(new ValidationError(FIELD_TITLE, "Title is required."));
            return;
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            errors.add(new ValidationError(FIELD_TITLE,
                    "Title must be at most " + TITLE_MAX_LENGTH + " characters."));
        }
    }

    private void validateDescription(Description description, List<ValidationError> errors) {
        String text = description.text();

        if (text.length() > DESCRIPTION_MAX_LENGTH) {
            errors.add(new ValidationError(FIELD_DESCRIPTION,
                    "Description must be at most " + DESCRIPTION_MAX_LENGTH + " characters."));
        }

        List<ImageSegment> images = DescriptionParser.images(text);
        for (int i = 0; i < images.size(); i++) {
            if (!images.get(i).source().isAbsolute()) {
                errors.add(new ValidationError(FIELD_DESCRIPTION,
                        "Image " + (i + 1)
                                + ": address must be absolute, including https:// or file://."));
            }
        }
    }

    private void validateTags(Set<Tag> tags, List<ValidationError> errors) {
        if (tags.size() > MAX_TAGS) {
            errors.add(new ValidationError(FIELD_TAGS,
                    "An idea can have at most " + MAX_TAGS + " tags."));
        }
    }
}
