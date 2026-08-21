package com.emgi.timeline.domain.content;

import java.util.Objects;

public record TextBlock(String text) implements ContentBlock {

    public TextBlock {
        Objects.requireNonNull(text, "text");
    }
}
