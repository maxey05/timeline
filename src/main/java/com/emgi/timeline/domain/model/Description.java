package com.emgi.timeline.domain.model;

import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.TextBlock;
import java.util.List;
import java.util.Objects;

/**
 * An idea's body: an ordered list of typed blocks (ARCHITECTURE.md §4.4).
 *
 * <p>The domain never knows what a block looks like — no HTML, no fonts, no pixels. Rendering is
 * the view's job ({@code BlockRenderer}, Phase 6) and storage is the repository's
 * ({@code idea_block}, Phase 2).
 */
public record Description(List<ContentBlock> blocks) {

    /** Character appended when {@link #plainTextPreview(int)} truncates. */
    public static final String ELLIPSIS = "…";

    public Description {
        Objects.requireNonNull(blocks, "blocks");
        blocks = List.copyOf(blocks); // immutable snapshot; also rejects null elements
    }

    public static Description empty() {
        return new Description(List.of());
    }

    /** A description holding one text block. This is all Phase 4 can create; Phase 6 adds the rest. */
    public static Description ofText(String text) {
        return new Description(List.of(new TextBlock(text)));
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /**
     * A single-line summary for list rows and title search.
     *
     * <p>Concatenates the text of {@link TextBlock}s in order, separated by a single space, and
     * skips link and image blocks entirely — a preview reading "https://very-long-url..." would
     * crowd out the text the user actually wrote. All whitespace (including newlines) collapses to
     * single spaces so a multi-line block can't break a one-line cell.
     *
     * <p>{@code maxChars} is the length of the <em>returned</em> string, ellipsis included: a
     * result of exactly {@code maxChars} characters is possible, longer is not. Truncation never
     * splits a surrogate pair, so an emoji at the boundary is dropped whole rather than turned into
     * a broken half-character.
     *
     * @throws IllegalArgumentException if {@code maxChars < 1}
     */
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
            cut--; // don't leave a dangling half of a surrogate pair
        }
        return flattened.substring(0, cut).stripTrailing() + ELLIPSIS;
    }
}
