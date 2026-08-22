package com.emgi.timeline.domain.content;

import java.util.List;
import java.util.Objects;

/**
 * A run of consecutive description lines that were not image tokens.
 *
 * <p>Line breaks inside the paragraph are preserved in the run text; the renderer wraps
 * and lays it out. The runs alternate freely between plain text and links.
 */
public record ParagraphSegment(List<TextRun> runs) implements DescriptionSegment {

    public ParagraphSegment {
        Objects.requireNonNull(runs, "runs");
        runs = List.copyOf(runs);
    }

    /** The paragraph with its links flattened back to their visible text. */
    public String plainText() {
        StringBuilder text = new StringBuilder();
        for (TextRun run : runs) {
            text.append(run.text());
        }
        return text.toString();
    }
}
