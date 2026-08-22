package com.emgi.timeline.domain.content;

import java.util.List;
import java.util.Objects;

public record ParagraphSegment(List<TextRun> runs) implements DescriptionSegment {

    public ParagraphSegment {
        Objects.requireNonNull(runs, "runs");
        runs = List.copyOf(runs);
    }

    public String plainText() {
        StringBuilder text = new StringBuilder();
        for (TextRun run : runs) {
            text.append(run.text());
        }
        return text.toString();
    }
}
