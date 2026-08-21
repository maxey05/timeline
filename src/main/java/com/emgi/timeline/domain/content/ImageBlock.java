package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.Objects;

public record ImageBlock(URI source, String altText) implements ContentBlock {

    public ImageBlock {
        Objects.requireNonNull(source, "source");
        if (altText == null) {
            altText = "";
        }
    }

    public static ImageBlock of(URI source) {
        return new ImageBlock(source, "");
    }
}
