package com.emgi.timeline.domain.model;

import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.TextBlock;
import java.util.List;
import java.util.Objects;

public record Description(List<ContentBlock> blocks) {

    public static final String ELLIPSIS = "…";

    public Description {
        Objects.requireNonNull(blocks, "blocks");
        blocks = List.copyOf(blocks);
    }

    public static Description empty() {
        return new Description(List.of());
    }

    public static Description ofText(String text) {
        return new Description(List.of(new TextBlock(text)));
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public String plainTextPreview(int maxChars) {
        if (maxChars < 1) {
            throw new IllegalArgumentException("maxChars must be at least 1, was " + maxChars);
        }

        StringBuilder joined = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock text) {
                if (!joined.isEmpty()) {
                    joined.append(' ');
                }
                joined.append(text.text());
            }
        }

        String flattened = joined.toString().replaceAll("\\s+", " ").strip();
        if (flattened.length() <= maxChars) {
            return flattened;
        }

        int cut = maxChars - ELLIPSIS.length();
        if (cut > 0 && Character.isHighSurrogate(flattened.charAt(cut - 1))) {
            cut--;
        }
        return flattened.substring(0, cut).stripTrailing() + ELLIPSIS;
    }
}
