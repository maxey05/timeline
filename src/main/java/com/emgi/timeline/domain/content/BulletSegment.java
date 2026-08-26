package com.emgi.timeline.domain.content;

import java.util.List;
import java.util.Objects;

public record BulletSegment(List<TextRun> runs) implements DescriptionSegment {

    public BulletSegment {
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
