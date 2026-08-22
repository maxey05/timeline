package com.emgi.timeline.domain.content;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the single description string into the segments the detail panel draws.
 *
 * <p>The description is plain text with exactly two conventions, and no others:
 *
 * <ol>
 *   <li>A line that is nothing but {@code ![alt](uri)} is an image.</li>
 *   <li>Anything that looks like a web address inside ordinary text is a link.</li>
 * </ol>
 *
 * <p>Both conventions are forgiving by design. A token whose address will not parse is
 * left as literal text rather than reported as an error, so a user who types a stray
 * {@code ![} sees exactly what they typed instead of a validation message they cannot
 * act on. The only thing that is ever an error is an image address that parses but is
 * relative, because that one silently fails to load a picture.
 */
public final class DescriptionParser {

    /** A whole line consisting of one image token. */
    private static final Pattern IMAGE_LINE =
            Pattern.compile("^\\s*!\\[([^\\]]*)\\]\\((\\S+)\\)\\s*$");

    /** A candidate web address. Trailing punctuation is trimmed afterwards. */
    private static final Pattern ADDRESS =
            Pattern.compile("(?i)(?:https?://|file://|www\\.)\\S+");

    /** Sentence punctuation that a URL at the end of a sentence must not swallow. */
    private static final String TRAILING_PUNCTUATION = ".,;:!?\"'";

    private DescriptionParser() {
    }

    /** Splits {@code text} into paragraphs and images, in the order they appear. */
    public static List<DescriptionSegment> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<DescriptionSegment> segments = new ArrayList<>();
        List<String> pending = new ArrayList<>();

        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            Matcher matcher = IMAGE_LINE.matcher(line);
            URI source = matcher.matches() ? asUri(matcher.group(2)) : null;

            if (source == null) {
                pending.add(line);
                continue;
            }

            flush(pending, segments);
            segments.add(new ImageSegment(source, matcher.group(1).strip()));
        }

        flush(pending, segments);
        return List.copyOf(segments);
    }

    /** Just the images, for validation and for counting them in a message. */
    public static List<ImageSegment> images(String text) {
        List<ImageSegment> images = new ArrayList<>();
        for (DescriptionSegment segment : parse(text)) {
            if (segment instanceof ImageSegment image) {
                images.add(image);
            }
        }
        return List.copyOf(images);
    }

    /** The text form of an image, as the editor inserts it on a line of its own. */
    public static String imageToken(URI source, String altText) {
        String alt = altText == null ? "" : altText.replaceAll("[\\[\\]\\r\\n]", " ").strip();
        return "![" + alt + "](" + source + ")";
    }

    private static void flush(List<String> pending, List<DescriptionSegment> segments) {
        String paragraph = String.join("\n", pending).strip();
        pending.clear();

        if (!paragraph.isEmpty()) {
            segments.add(new ParagraphSegment(runsIn(paragraph)));
        }
    }

    /**
     * Splits one paragraph into alternating plain and link runs.
     *
     * <p>The cursor deliberately lags the matcher: when trailing punctuation is trimmed
     * off a match, the trimmed characters stay unclaimed and are picked up by the next
     * gap or by the tail, so nothing is ever dropped from the visible text.
     */
    static List<TextRun> runsIn(String paragraph) {
        List<TextRun> runs = new ArrayList<>();
        Matcher matcher = ADDRESS.matcher(paragraph);
        int cursor = 0;

        while (matcher.find()) {
            String candidate = trimTrailing(matcher.group());
            URI target = asAbsoluteUri(candidate);

            if (target == null) {
                continue;
            }

            if (matcher.start() > cursor) {
                runs.add(TextRun.plain(paragraph.substring(cursor, matcher.start())));
            }

            runs.add(TextRun.link(candidate, target));
            cursor = matcher.start() + candidate.length();
        }

        if (cursor < paragraph.length()) {
            runs.add(TextRun.plain(paragraph.substring(cursor)));
        }

        return runs.isEmpty() ? List.of(TextRun.plain(paragraph)) : List.copyOf(runs);
    }

    private static String trimTrailing(String candidate) {
        String result = candidate;

        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);

            boolean drop = TRAILING_PUNCTUATION.indexOf(last) >= 0
                    || (last == ')' && count(result, '(') < count(result, ')'))
                    || (last == ']' && count(result, '[') < count(result, ']'))
                    || (last == '>' && count(result, '<') < count(result, '>'));

            if (!drop) {
                break;
            }

            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    private static int count(String text, char target) {
        int found = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                found++;
            }
        }
        return found;
    }

    /** Parses an address, supplying the scheme a bare {@code www.} host leaves out. */
    private static URI asAbsoluteUri(String candidate) {
        String href = candidate.regionMatches(true, 0, "www.", 0, 4)
                ? "https://" + candidate
                : candidate;

        URI uri = asUri(href);
        return uri != null && uri.isAbsolute() ? uri : null;
    }

    private static URI asUri(String raw) {
        try {
            return new URI(raw);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
