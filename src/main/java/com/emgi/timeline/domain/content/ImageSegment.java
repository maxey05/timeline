package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.Objects;

/**
 * An image that occupied a line of its own in the description text.
 *
 * <p>The source is whatever the {@code ![alt](uri)} token carried. It parsed as a URI —
 * otherwise the parser would have left the line as text — but it is not necessarily
 * absolute; {@code IdeaValidator} is what rejects a relative one.
 */
public record ImageSegment(URI source, String altText) implements DescriptionSegment {

    public ImageSegment {
        Objects.requireNonNull(source, "source");
        if (altText == null) {
            altText = "";
        }
    }
}
