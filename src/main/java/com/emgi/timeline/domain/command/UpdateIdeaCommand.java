package com.emgi.timeline.domain.command;

import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import java.util.Objects;
import java.util.Set;

/**
 * The contents of an edited idea form, plus the id of the idea being edited (§7.2).
 *
 * <p>Same leniency as {@link CreateIdeaCommand} for {@code title}, and the same normalization for
 * the other fields — with one exception: {@code id} is rejected if null. An id is never typed by a
 * user; it comes from the row they selected. A null one is a wiring bug, and failing loudly at
 * construction beats reporting "id is required" in a form that has no id field.
 */
public record UpdateIdeaCommand(
        IdeaId id,
        String title,
        Description description,
        Set<Tag> tags,
        IdeaStatus status
) {

    public UpdateIdeaCommand {
        Objects.requireNonNull(id, "id");
        if (description == null) {
            description = Description.empty();
        }
        tags = (tags == null) ? Set.of() : Set.copyOf(tags);
        if (status == null) {
            status = IdeaStatus.INCOMPLETE;
        }
    }
}
