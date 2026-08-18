package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.Objects;

/**
 * A hyperlink. Covers the "just hyperlinks" requirement for video and everything else
 * (locked decision #7).
 *
 * <p>A null or blank {@code label} is normalized to the target's text form, so no renderer or
 * exporter downstream has to decide what to show when the user didn't name the link.
 *
 * <p>Whether the target is reachable — or even absolute — is not checked here. Absoluteness is a
 * validation rule ({@code IdeaValidator}); reachability is a rendering concern that shows a
 * placeholder rather than throwing (ARCHITECTURE.md §7.5).
 */
public record LinkBlock(URI target, String label) implements ContentBlock {

    public LinkBlock {
        Objects.requireNonNull(target, "target");
        if (label == null || label.isBlank()) {
            label = target.toString();
        }
    }

    /** A link that shows its own URL as the label. */
    public static LinkBlock of(URI target) {
        return new LinkBlock(target, null);
    }
}
