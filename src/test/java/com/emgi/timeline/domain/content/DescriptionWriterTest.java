package com.emgi.timeline.domain.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.emgi.timeline.domain.content.DescriptionWriter.Line;
import com.emgi.timeline.domain.content.DescriptionWriter.Picture;
import com.emgi.timeline.domain.content.DescriptionWriter.Piece;
import com.emgi.timeline.domain.content.DescriptionWriter.Words;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DescriptionWriterTest {

    private static final URI PNG = URI.create("file:///home/emgi/shot.png");

    private static final String TOKEN = "![](file:///home/emgi/shot.png)";

    @Test
    void noLinesIsEmptyText() {
        assertThat(DescriptionWriter.write(List.of())).isEmpty();
    }

    @Test
    @DisplayName("an empty line between two paragraphs survives")
    void blankLineSurvives() {
        String text = DescriptionWriter.write(
                List.of(Line.text("A"), Line.text(""), Line.text("test")));

        assertThat(text).isEqualTo("A\n\ntest");
    }

    @Test
    void consecutiveBlankLinesSurvive() {
        String text = DescriptionWriter.write(
                List.of(Line.text("A"), Line.text(""), Line.text(""), Line.text("B")));

        assertThat(text).isEqualTo("A\n\n\nB");
    }

    @Test
    @DisplayName("an image terminates its own line and the next line adds no second newline")
    void imageDoesNotDoubleItsNewline() {
        String text = DescriptionWriter.write(
                List.of(Line.text("A"), Line.picture(PNG), Line.text("B")));

        assertThat(text).isEqualTo("A\n" + TOKEN + "\nB");
    }

    @Test
    void imageIsPushedOntoItsOwnLineInsideAMixedParagraph() {
        String text = DescriptionWriter.write(List.of(
                new Line(false, List.of(new Words("A"), new Picture(PNG), new Words("B")))));

        assertThat(text).isEqualTo("A\n" + TOKEN + "\nB");
    }

    @Test
    void bulletGetsThePrefix() {
        String text = DescriptionWriter.write(
                List.of(Line.bulletText("one"), Line.bulletText("two")));

        assertThat(text).isEqualTo("- one\n- two");
    }

    @Test
    void emptyBulletKeepsThePrefix() {
        assertThat(DescriptionWriter.write(List.of(Line.bulletText("")))).isEqualTo("- ");
    }

    @Test
    @DisplayName("a bullet holding an image drops the prefix, or it would read back as text")
    void bulletWithImageDropsThePrefix() {
        List<Piece> pieces = List.of(new Picture(PNG));
        String text = DescriptionWriter.write(List.of(new Line(true, pieces)));

        assertThat(text).isEqualTo(TOKEN + "\n");
    }

    @Test
    void bulletsAndBlankLinesRoundTripThroughTheParser() {
        String text = DescriptionWriter.write(List.of(
                Line.text("A"),
                Line.text(""),
                Line.bulletText("one"),
                Line.bulletText("two"),
                Line.picture(PNG),
                Line.text("tail")));

        List<DescriptionSegment> segments = DescriptionParser.parse(text);

        assertThat(segments).hasSize(5);
        assertThat(segments.get(0)).isInstanceOf(ParagraphSegment.class);
        assertThat(((ParagraphSegment) segments.get(0)).plainText()).isEqualTo("A");
        assertThat(((BulletSegment) segments.get(1)).plainText()).isEqualTo("one");
        assertThat(((BulletSegment) segments.get(2)).plainText()).isEqualTo("two");
        assertThat(segments.get(3)).isInstanceOf(ImageSegment.class);
        assertThat(((ParagraphSegment) segments.get(4)).plainText()).isEqualTo("tail");
    }
}
