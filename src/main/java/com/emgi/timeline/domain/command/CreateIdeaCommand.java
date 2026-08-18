package com.emgi.timeline.domain.command;

import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import java.util.Set;

/**
 * The contents of a filled-in "new idea" form — no id, no timestamps, because a half-filled form
 * has neither (ARCHITECTURE.md §7.1). The service assigns both, which is what keeps {@code Idea}
 * from ever existing in an invalid state.
 *
 * <p><strong>{@code title} is deliberately permitted to be null or blank here.</strong> It is the
 * one field the user types freely, so "missing title" has to survive long enough to be reported as
 * a friendly form error by {@code IdeaValidator} rather than blowing up as an exception on the way
 * in. Everything else is normalized to a safe default, so the validator can be about rules instead
 * of null-checks.
 */
public record CreateIdeaCommand(
        String title,
        Description description,
        Set<Tag> tags,
        IdeaStatus status
) {

    public CreateIdeaCommand {
        if (description == null) {
            description = Description.empty();
        }
        tags = (tags == null) ? Set.of() : Set.copyOf(tags);
        if (status == null) {
            status = IdeaStatus.INCOMPLETE;
        }
    }

    /** The common case: a title and nothing else. */
    public static CreateIdeaCommand of(String title) {
        return new CreateIdeaCommand(title, null, null, null);
    }
}
