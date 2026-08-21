package com.emgi.timeline.domain.command;

import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import java.util.Set;

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

    public static CreateIdeaCommand of(String title) {
        return new CreateIdeaCommand(title, null, null, null);
    }
}
