package com.emgi.timeline.domain.content;

import java.util.Objects;

/**
 * A paragraph of plain text.
 *
 * <p>Empty text is allowed on purpose: a user who adds a block and hasn't typed into it yet holds
 * a valid, empty {@code TextBlock}. Length limits are a validation concern, not a construction one
 * — see {@code IdeaValidator}.
 */
public record TextBlock(String text) implements ContentBlock {

    public TextBlock {
        Objects.requireNonNull(text, "text");
    }
}
