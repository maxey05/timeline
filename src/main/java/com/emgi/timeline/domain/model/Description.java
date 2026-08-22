package com.emgi.timeline.domain.model;

import com.emgi.timeline.domain.content.DescriptionParser;
import com.emgi.timeline.domain.content.DescriptionSegment;
import com.emgi.timeline.domain.content.ImageSegment;
import com.emgi.timeline.domain.content.ParagraphSegment;
import java.util.List;
import java.util.Objects;

/**
 * The body of an idea: one uniform block of text.
 *
 * <p>This used to be an ordered list of typed blocks. It is now a single string, and the
 * structure that used to be modelled — links, images — is a convention <em>inside</em>
 * that string, parsed on the way to the screen by {@link DescriptionParser}. The trade is
 * deliberate: the editor becomes one box the user types into, and nothing in the domain,
 * the schema, or the repository has to know that images exist.
 */
public record Description(String text) {

    public static final String ELLIPSIS = "…";

    public Description {
        Objects.requireNonNull(text, "text");
    }

    public static Description empty() {
        return new Description("");
    }

    public static Description ofText(String text) {
        return new Description(text);
    }

    public boolean isEmpty() {
        return text.isBlank();
    }

    /**
     * True when this description holds at least one image and nothing else.
     *
     * <p>{@link #plainTextPreview} drops image tokens, so a description of nothing but
     * pictures previews as an empty string -- indistinguishable, in the list, from an idea
     * with no description at all. This is the question the list asks to tell those two
     * apart; what it then says instead is the list's business, not the domain's.</p>
     */
    public boolean hasOnlyImages() {
        List<DescriptionSegment> segments = DescriptionParser.parse(text);

        if (segments.isEmpty()) {
            return false;
        }

        for (DescriptionSegment segment : segments) {
            if (!(segment instanceof ImageSegment)) {
                return false;
            }
        }

        return true;
    }

    /**
     * A one-line summary for the idea list.
     *
     * <p>Image tokens are dropped rather than shown as their raw {@code ![](…)} source —
     * a preview is for reading, and the file name of a screenshot tells the reader
     * nothing. Links keep their visible text.
     */
    public String plainTextPreview(int maxChars) {
        if (maxChars < 1) {
            throw new IllegalArgumentException("maxChars must be at least 1, was " + maxChars);
        }

        StringBuilder joined = new StringBuilder();
        for (DescriptionSegment segment : DescriptionParser.parse(text)) {
            if (segment instanceof ParagraphSegment paragraph) {
                if (!joined.isEmpty()) {
                    joined.append(' ');
                }
                joined.append(paragraph.plainText());
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
