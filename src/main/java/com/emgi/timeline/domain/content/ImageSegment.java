package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.Objects;

public record ImageSegment(URI source, String altText) implements DescriptionSegment {

    public ImageSegment {
        Objects.requireNonNull(source, "source");
        if (altText == null) {
            altText = "";
        }
    }
}
