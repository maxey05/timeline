package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.Objects;

public record LinkBlock(URI target, String label) implements ContentBlock {

    public LinkBlock {
        Objects.requireNonNull(target, "target");
        if (label == null || label.isBlank()) {
            label = target.toString();
        }
    }

    public static LinkBlock of(URI target) {
        return new LinkBlock(target, null);
    }
}
