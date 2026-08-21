package com.emgi.timeline.domain.validation;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
import com.emgi.timeline.domain.content.TextBlock;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Tag;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IdeaValidator {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_TAGS = "tags";

    public static final int TITLE_MAX_LENGTH = 120;
    public static final int TEXT_BLOCK_MAX_LENGTH = 10_000;
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
        List<ContentBlock> blocks = description.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            int position = i + 1;
            switch (blocks.get(i)) {
                case TextBlock text -> {
                    if (text.text().length() > TEXT_BLOCK_MAX_LENGTH) {
                        errors.add(new ValidationError(FIELD_DESCRIPTION,
                                "Block " + position + ": text must be at most "
                                        + TEXT_BLOCK_MAX_LENGTH + " characters."));
                    }
                }
                case LinkBlock link -> requireAbsolute(link.target(), position, "link", errors);
                case ImageBlock image -> requireAbsolute(image.source(), position, "image", errors);
            }
        }
    }

    private void requireAbsolute(URI uri, int position, String kind, List<ValidationError> errors) {
        if (!uri.isAbsolute()) {
            errors.add(new ValidationError(FIELD_DESCRIPTION,
                    "Block " + position + ": " + kind
                            + " address must be absolute, including https:// or file://."));
        }
    }

    private void validateTags(Set<Tag> tags, List<ValidationError> errors) {
        if (tags.size() > MAX_TAGS) {
            errors.add(new ValidationError(FIELD_TAGS,
                    "An idea can have at most " + MAX_TAGS + " tags."));
        }
    }
}
