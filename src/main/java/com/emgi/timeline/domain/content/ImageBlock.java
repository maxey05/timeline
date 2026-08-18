package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.Objects;

/**
 * An image, held <em>by reference</em> (locked decision #6). Nothing is copied or embedded, so if
 * the file behind {@code source} moves the reference breaks and the UI falls back to
 * {@link #altText()} — the failure is visible and cheap rather than a silent duplicate store.
 *
 * <p>A null {@code altText} is normalized to the empty string; the renderer decides what to show
 * when there is no alt text, and never has to null-check.
 */
public record ImageBlock(URI source, String altText) implements ContentBlock {

    public ImageBlock {
        Objects.requireNonNull(source, "source");
        if (altText == null) {
            altText = "";
        }
    }

    /** An image with no alt text. */
    public static ImageBlock of(URI source) {
        return new ImageBlock(source, "");
    }
}
