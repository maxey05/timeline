package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public final class DescriptionWriter {

    public static final String BULLET_PREFIX = "- ";

    public sealed interface Piece permits Words, Picture {
    }

    public record Words(String value) implements Piece {

        public Words {
            Objects.requireNonNull(value, "value");
        }
    }

    public record Picture(URI source) implements Piece {

        public Picture {
            Objects.requireNonNull(source, "source");
        }
    }

    public record Line(boolean bullet, List<Piece> pieces) {

        public Line {
            Objects.requireNonNull(pieces, "pieces");
            pieces = List.copyOf(pieces);
        }

        public static Line text(String value) {
            return new Line(false, List.of(new Words(value)));
        }

        public static Line bulletText(String value) {
            return new Line(true, List.of(new Words(value)));
        }

        public static Line picture(URI source) {
            return new Line(false, List.of(new Picture(source)));
        }

        boolean holdsPicture() {
            for (Piece piece : pieces) {
                if (piece instanceof Picture) {
                    return true;
                }
            }
            return false;
        }
    }

    private DescriptionWriter() {
    }

    public static String write(List<Line> lines) {
        Objects.requireNonNull(lines, "lines");

        StringBuilder text = new StringBuilder();
        boolean terminated = false;

        for (int index = 0; index < lines.size(); index++) {
            if (index > 0 && !terminated) {
                text.append('\n');
            }

            terminated = appendLine(text, lines.get(index));
        }

        return text.toString();
    }

    private static boolean appendLine(StringBuilder text, Line line) {
        if (line.bullet() && !line.holdsPicture()) {
            text.append(BULLET_PREFIX);
        }

        boolean terminated = false;

        for (Piece piece : line.pieces()) {
            if (piece instanceof Words words) {
                if (!words.value().isEmpty()) {
                    text.append(words.value());
                    terminated = false;
                }
                continue;
            }

            Picture picture = (Picture) piece;

            endLine(text);
            text.append(DescriptionParser.imageToken(picture.source(), ""));
            text.append('\n');
            terminated = true;
        }

        return terminated;
    }

    private static void endLine(StringBuilder text) {
        if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
    }
}
