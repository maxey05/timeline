package com.emgi.timeline.domain.content;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DescriptionParser {

    private static final Pattern IMAGE_LINE =
            Pattern.compile("^\\s*!\\[([^\\]]*)\\]\\((\\S+)\\)\\s*$");

    private static final Pattern BULLET_LINE =
            Pattern.compile("^-\\x20(.*)$");

    private static final Pattern ADDRESS =
            Pattern.compile("(?i)(?:https?://|file://|www\\.)\\S+");

    private static final String TRAILING_PUNCTUATION = ".,;:!?\"'";

    private DescriptionParser() {
    }

    public static List<DescriptionSegment> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<DescriptionSegment> segments = new ArrayList<>();
        List<String> pending = new ArrayList<>();

        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            Matcher matcher = IMAGE_LINE.matcher(line);
            URI source = matcher.matches() ? asUri(matcher.group(2)) : null;

            if (source != null) {
                flush(pending, segments);
                segments.add(new ImageSegment(source, matcher.group(1).strip()));
                continue;
            }

            Matcher bullet = BULLET_LINE.matcher(line);

            if (bullet.matches()) {
                flush(pending, segments);
                segments.add(new BulletSegment(runsIn(bullet.group(1))));
                continue;
            }

            pending.add(line);
        }

        flush(pending, segments);
        return List.copyOf(segments);
    }

    public static List<ImageSegment> images(String text) {
        List<ImageSegment> images = new ArrayList<>();
        for (DescriptionSegment segment : parse(text)) {
            if (segment instanceof ImageSegment image) {
                images.add(image);
            }
        }
        return List.copyOf(images);
    }

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
