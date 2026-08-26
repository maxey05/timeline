package com.emgi.timeline.domain.model;

import com.emgi.timeline.domain.content.BulletSegment;
import com.emgi.timeline.domain.content.DescriptionParser;
import com.emgi.timeline.domain.content.DescriptionSegment;
import com.emgi.timeline.domain.content.ImageSegment;
import com.emgi.timeline.domain.content.ParagraphSegment;
import java.util.List;
import java.util.Objects;

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

    public String plainTextPreview(int maxChars) {
        if (maxChars < 1) {
            throw new IllegalArgumentException("maxChars must be at least 1, was " + maxChars);
        }

        String firstLine = "";
        for (DescriptionSegment segment : DescriptionParser.parse(text)) {
            String piece = switch (segment) {
                case ParagraphSegment paragraph -> firstLineOf(paragraph.plainText());
                case BulletSegment bullet -> bullet.plainText();
                case ImageSegment image -> "";
            };

            if (!piece.isEmpty()) {
                firstLine = piece;
                break;
            }
        }

        String flattened = firstLine.replaceAll("\\s+", " ").strip();
        if (flattened.length() <= maxChars) {
            return flattened;
        }

        int cut = maxChars - ELLIPSIS.length();
        if (cut > 0 && Character.isHighSurrogate(flattened.charAt(cut - 1))) {
            cut--;
        }
        return flattened.substring(0, cut).stripTrailing() + ELLIPSIS;
    }

    private static String firstLineOf(String text)
    {
        int newline = text.indexOf('\n');
        return newline == -1 ? text : text.substring(0, newline);
    }
}
