package com.emgi.timeline.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DescriptionTest {

    @Test
    void emptyDescriptionHasNoText() {
        assertThat(Description.empty().text()).isEmpty();
        assertThat(Description.empty().isEmpty()).isTrue();
    }

    @Test
    void ofTextHoldsTheTextVerbatim() {
        assertThat(Description.ofText("Hello").text()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("a description of nothing but whitespace counts as empty")
    void whitespaceOnlyDescriptionIsEmpty() {
        assertThat(new Description("   \n\t ").isEmpty()).isTrue();
    }

    @Test
    void rejectsNullText() {
        assertThatThrownBy(() -> new Description(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void previewOfAnEmptyDescriptionIsEmpty() {
        assertThat(Description.empty().plainTextPreview(20)).isEmpty();
    }

    @Test
    void previewReturnsShortTextUnchanged() {
        assertThat(Description.ofText("Short").plainTextPreview(20)).isEqualTo("Short");
    }

    @Test
    @DisplayName("preview drops image tokens rather than showing their raw source")
    void previewSkipsImages() {
        Description description = Description.ofText(
                "before\n![a diagram](file:///home/emgi/diagram.png)\nafter");

        assertThat(description.plainTextPreview(50)).isEqualTo("before after");
    }

    @Test
    @DisplayName("preview keeps a link's visible text, because that is what the user typed")
    void previewKeepsLinkText() {
        Description description = Description.ofText("see https://example.com/spec for details");

        assertThat(description.plainTextPreview(60))
                .isEqualTo("see https://example.com/spec for details");
    }

    @Test
    @DisplayName("paragraphs separated by an image are joined with a single space")
    void previewJoinsParagraphsWithASingleSpace() {
        Description description = Description.ofText(
                "first\n![](https://example.com/a.png)\nsecond");

        assertThat(description.plainTextPreview(50)).isEqualTo("first second");
    }

    @Test
    @DisplayName("newlines and runs of whitespace collapse, so a preview stays on one line")
    void previewCollapsesWhitespace() {
        Description description = Description.ofText("  line one\n\n\tline   two  ");

        assertThat(description.plainTextPreview(50)).isEqualTo("line one line two");
    }

    @Test
    @DisplayName("maxChars is the length of the result, ellipsis included")
    void previewTruncatesToExactlyMaxChars() {
        Description description = Description.ofText("abcdefghij");

        String preview = description.plainTextPreview(5);

        assertThat(preview).isEqualTo("abcd" + Description.ELLIPSIS);
        assertThat(preview).hasSize(5);
    }

    @Test
    void previewDoesNotTruncateTextOfExactlyMaxChars() {
        assertThat(Description.ofText("abcde").plainTextPreview(5)).isEqualTo("abcde");
    }

    @Test
    @DisplayName("truncation does not leave a trailing space before the ellipsis")
    void previewTrimsBeforeEllipsis() {
        Description description = Description.ofText("ab cdefgh");

        assertThat(description.plainTextPreview(4)).isEqualTo("ab" + Description.ELLIPSIS);
    }

    @Test
    @DisplayName("truncation never splits an emoji in half")
    void previewDoesNotSplitSurrogatePairs() {
        Description description = Description.ofText("ab🚀cd");

        String preview = description.plainTextPreview(4);

        assertThat(preview).isEqualTo("ab" + Description.ELLIPSIS);
        assertThat(Character.isSurrogate(preview.charAt(preview.length() - 2))).isFalse();
    }

    @Test
    void previewRejectsNonPositiveMaxChars() {
        Description description = Description.ofText("anything");

        assertThatThrownBy(() -> description.plainTextPreview(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> description.plainTextPreview(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a description with no content at all is not an images-only one")
    void emptyDescriptionHasNoImages() {
        assertThat(Description.empty().hasOnlyImages()).isFalse();
        assertThat(new Description("  \n ").hasOnlyImages()).isFalse();
    }

    @Test
    void textOnlyDescriptionIsNotImagesOnly() {
        assertThat(Description.ofText("Just words").hasOnlyImages()).isFalse();
    }

    @Test
    @DisplayName("one image on its own is images-only")
    void aSingleImageIsImagesOnly() {
        Description description = Description.ofText("![](file:///tmp/shot.png)");

        assertThat(description.hasOnlyImages()).isTrue();
        assertThat(description.plainTextPreview(40)).isEmpty();
    }

    @Test
    @DisplayName("several images, blank lines and all, are still images-only")
    void severalImagesAreImagesOnly() {
        Description description = Description.ofText(
                "![](file:///tmp/one.png)\n\n![alt](file:///tmp/two.png)\n");

        assertThat(description.hasOnlyImages()).isTrue();
    }

    @Test
    @DisplayName("a single word beside the pictures is enough to stop it being images-only")
    void imagesWithAnyTextAreNotImagesOnly() {
        Description description = Description.ofText(
                "Look:\n![](file:///tmp/one.png)");

        assertThat(description.hasOnlyImages()).isFalse();
    }

    @Test
    @DisplayName("a malformed image token is text, so it is not images-only")
    void aMalformedImageTokenIsNotAnImage() {
        assertThat(Description.ofText("![](file:///tmp/one.png").hasOnlyImages()).isFalse();
    }
}
