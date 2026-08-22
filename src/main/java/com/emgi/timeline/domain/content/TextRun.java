package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.Objects;

/**
 * A stretch of paragraph text that is either plain or a link.
 *
 * <p>{@code target} is {@code null} for plain text. That nullable field is deliberate: a
 * sealed pair of record types would read better in isolation but forces every renderer
 * into a switch for what is really one question, {@link #isLink()}.
 */
public record TextRun(String text, URI target) {

    public TextRun {
        Objects.requireNonNull(text, "text");
    }

    /** A run of ordinary text. */
    public static TextRun plain(String text) {
        return new TextRun(text, null);
    }

    /** A run whose visible text is {@code text} and which opens {@code target} when clicked. */
    public static TextRun link(String text, URI target) {
        return new TextRun(text, Objects.requireNonNull(target, "target"));
    }

    public boolean isLink() {
        return target != null;
    }
}
