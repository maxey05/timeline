package com.emgi.timeline.domain.command;

import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import java.util.Objects;
import java.util.Set;

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
