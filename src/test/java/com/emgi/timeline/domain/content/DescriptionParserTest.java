package com.emgi.timeline.domain.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The parse is where the description's two conventions actually live, so this is where
 * they are pinned down. The old block renderer had no automated test at all — it was the
 * one named coverage gap of the rich-content phase — because everything worth checking
 * about it was tangled up in scene-graph nodes. Deriving the structure in the domain
 * instead means the interesting half is testable without a toolkit.
 */
class DescriptionParserTest {

    private static final String PNG = "file:///home/emgi/shot.png";

    // ---------------------------------------------------------------- paragraphs

    @Test
    void emptyTextHasNoSegments() {
        assertThat(DescriptionParser.parse("")).isEmpty();
        assertThat(DescriptionParser.parse(null)).isEmpty();
        assertThat(DescriptionParser.parse("   \n\n  ")).isEmpty();
    }

    @Test
    void plainTextIsOneParagraphOfOnePlainRun() {
        List<DescriptionSegment> segments = DescriptionParser.parse("just words");

        assertThat(segments).hasSize(1);
        assertThat(runsOf("just words")).containsExactly(TextRun.plain("just words"));
    }

    @Test
    @DisplayName("blank lines do not split paragraphs — only an image does")
    void blankLinesStayInsideOneParagraph() {
        List<DescriptionSegment> segments = DescriptionParser.parse("one\n\ntwo");

        assertThat(segments).hasSize(1);
        assertThat(paragraphAt(segments, 0).plainText()).isEqualTo("one\n\ntwo");
    }

    @Test
    @DisplayName("leading and trailing blank lines are trimmed off a paragraph")
    void paragraphEdgesAreStripped() {
        List<DescriptionSegment> segments = DescriptionParser.parse("\n\n  body  \n\n");

        assertThat(paragraphAt(segments, 0).plainText()).isEqualTo("body");
    }

    @Test
    void carriageReturnsAreNormalisedBeforeSplitting() {
        List<DescriptionSegment> segments =
                DescriptionParser.parse("above\r\n![](" + PNG + ")\r\nbelow");

        assertThat(segments).hasSize(3);
        assertThat(segments.get(1) instanceof ImageSegment).isTrue();
    }

    // ---------------------------------------------------------------- images

    @Test
    void anImageOnItsOwnLineBecomesAnImageSegment() {
        List<DescriptionSegment> segments = DescriptionParser.parse("![a screenshot](" + PNG + ")");

        assertThat(segments).hasSize(1);
        ImageSegment image = imageAt(segments, 0);
        assertThat(image.source()).isEqualTo(URI.create(PNG));
        assertThat(image.altText()).isEqualTo("a screenshot");
    }

    @Test
    void anImageSplitsTheTextAroundIt() {
        List<DescriptionSegment> segments =
                DescriptionParser.parse("above\n![](" + PNG + ")\nbelow");

        assertThat(segments).hasSize(3);
        assertThat(paragraphAt(segments, 0).plainText()).isEqualTo("above");
        assertThat(segments.get(1) instanceof ImageSegment).isTrue();
        assertThat(paragraphAt(segments, 2).plainText()).isEqualTo("below");
    }

    @Test
    void consecutiveImagesEachGetTheirOwnSegment() {
        List<DescriptionSegment> segments =
                DescriptionParser.parse("![](" + PNG + ")\n![](" + PNG + ")");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0) instanceof ImageSegment).isTrue();
        assertThat(segments.get(1) instanceof ImageSegment).isTrue();
    }

    @Test
    @DisplayName("a token sharing its line with words is text, not an image")
    void aTokenWithNeighboursOnItsLineStaysText() {
        List<DescriptionSegment> segments =
                DescriptionParser.parse("look: ![](" + PNG + ") there");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0) instanceof ParagraphSegment).isTrue();
    }

    @Test
    @DisplayName("a token whose address will not parse renders as the literal text typed")
    void anUnparseableImageAddressStaysText() {
        List<DescriptionSegment> segments = DescriptionParser.parse("![](https://exa[mple)");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0) instanceof ParagraphSegment).isTrue();
    }

    @Test
    @DisplayName("a relative address still parses as an image, so the validator can reject it")
    void aRelativeImageAddressIsStillAnImage() {
        List<DescriptionSegment> segments = DescriptionParser.parse("![](shot.png)");

        assertThat(imageAt(segments, 0).source().isAbsolute()).isFalse();
    }

    @Test
    void imagesReturnsOnlyTheImages() {
        List<ImageSegment> images = DescriptionParser.images(
                "words\n![one](" + PNG + ")\nmore\n![two](" + PNG + ")");

        assertThat(images).hasSize(2);
        assertThat(images.get(0).altText()).isEqualTo("one");
        assertThat(images.get(1).altText()).isEqualTo("two");
    }

    // ---------------------------------------------------------------- tokens

    @Test
    void aTokenTheParserEmitsIsATokenTheParserReads() {
        String token = DescriptionParser.imageToken(URI.create(PNG), "alt words");

        ImageSegment image = imageAt(DescriptionParser.parse(token), 0);

        assertThat(image.source()).isEqualTo(URI.create(PNG));
        assertThat(image.altText()).isEqualTo("alt words");
    }

    @Test
    @DisplayName("brackets and newlines in alt text cannot break the token they sit in")
    void altTextIsSanitised() {
        String token = DescriptionParser.imageToken(URI.create(PNG), "a [weird]\nlabel");

        assertThat(token.contains("[weird]")).isFalse();
        assertThat(token.contains("\n")).isFalse();
        assertThat(DescriptionParser.parse(token).get(0) instanceof ImageSegment).isTrue();
    }

    @Test
    void nullAltTextBecomesAnEmptyLabel() {
        assertThat(DescriptionParser.imageToken(URI.create(PNG), null))
                .isEqualTo("![](" + PNG + ")");
    }

    // ---------------------------------------------------------------- links

    @Test
    void anAddressInsideASentenceBecomesItsOwnRun() {
        List<TextRun> runs = runsOf("see https://example.com/spec today");

        assertThat(runs).hasSize(3);
        assertThat(runs.get(0)).isEqualTo(TextRun.plain("see "));
        assertThat(runs.get(1).isLink()).isTrue();
        assertThat(runs.get(1).text()).isEqualTo("https://example.com/spec");
        assertThat(runs.get(1).target()).isEqualTo(URI.create("https://example.com/spec"));
        assertThat(runs.get(2)).isEqualTo(TextRun.plain(" today"));
    }

    @Test
    @DisplayName("a sentence-ending full stop is not part of the address")
    void trailingSentencePunctuationIsNotSwallowed() {
        List<TextRun> runs = runsOf("read https://example.com/a.");

        assertThat(runs.get(1).text()).isEqualTo("https://example.com/a");
        assertThat(runs.get(2).text()).isEqualTo(".");
    }

    @Test
    @DisplayName("an unbalanced closing bracket belongs to the sentence, not the address")
    void anUnbalancedClosingParenIsNotSwallowed() {
        List<TextRun> runs = runsOf("(see https://example.com/a)");

        assertThat(runs.get(1).text()).isEqualTo("https://example.com/a");
        assertThat(runs.get(2).text()).isEqualTo(")");
    }

    @Test
    @DisplayName("balanced brackets inside an address are kept — Wikipedia URLs need them")
    void balancedParensStayInTheAddress() {
        List<TextRun> runs = runsOf("https://en.wikipedia.org/wiki/Fox_(disambiguation)");

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).text())
                .isEqualTo("https://en.wikipedia.org/wiki/Fox_(disambiguation)");
    }

    @Test
    @DisplayName("a bare www host gets the scheme it needs without changing what is shown")
    void bareWwwHostsGetAScheme() {
        List<TextRun> runs = runsOf("www.example.com");

        assertThat(runs.get(0).text()).isEqualTo("www.example.com");
        assertThat(runs.get(0).target()).isEqualTo(URI.create("https://www.example.com"));
    }

    @Test
    void severalAddressesInOneParagraphAllBecomeLinks() {
        List<TextRun> runs = runsOf("a https://one.example b https://two.example c");

        assertThat(linkCount(runs)).isEqualTo(2);
        assertThat(plainTextOf(runs)).isEqualTo("a https://one.example b https://two.example c");
    }

    @Test
    void fileAddressesAreLinksToo() {
        List<TextRun> runs = runsOf("saved at file:///home/emgi/notes.txt");

        assertThat(runs.get(1).isLink()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no addresses here at all",
            "http:// on its own is not an address",
            "mention http but never a scheme"})
    void textWithoutAnAddressIsOnePlainRun(String text) {
        List<TextRun> runs = runsOf(text);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).isLink()).isFalse();
    }

    @Test
    @DisplayName("an address that will not parse is left as ordinary text")
    void anUnparseableLinkAddressIsNotALink() {
        List<TextRun> runs = runsOf("try https://exa[mple");

        assertThat(linkCount(runs)).isEqualTo(0);
        assertThat(plainTextOf(runs)).isEqualTo("try https://exa[mple");
    }

    @Test
    @DisplayName("no character is ever lost, whatever the trimming does")
    void everyCharacterSurvivesTheSplit() {
        String text = "(https://a.example/x). then https://b.example/y, end";

        assertThat(plainTextOf(runsOf(text))).isEqualTo(text);
    }

    // ---------------------------------------------------------------- helpers

    private static ParagraphSegment paragraphAt(List<DescriptionSegment> segments, int index) {
        return (ParagraphSegment) segments.get(index);
    }

    private static ImageSegment imageAt(List<DescriptionSegment> segments, int index) {
        return (ImageSegment) segments.get(index);
    }

    private static List<TextRun> runsOf(String paragraph) {
        return paragraphAt(DescriptionParser.parse(paragraph), 0).runs();
    }

    private static String plainTextOf(List<TextRun> runs) {
        return new ParagraphSegment(runs).plainText();
    }

    private static int linkCount(List<TextRun> runs) {
        int found = 0;
        for (TextRun run : runs) {
            if (run.isLink()) {
                found++;
            }
        }
        return found;
    }
}
