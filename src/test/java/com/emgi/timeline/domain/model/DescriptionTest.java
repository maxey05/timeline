package com.emgi.timeline.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
import com.emgi.timeline.domain.content.TextBlock;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DescriptionTest {

    private static final URI SOMEWHERE = URI.create("https://example.com/a");

    @Test
    void emptyDescriptionHasNoBlocks() {
        assertThat(Description.empty().blocks()).isEmpty();
        assertThat(Description.empty().isEmpty()).isTrue();
    }

    @Test
    void ofTextHoldsASingleTextBlock() {
        Description description = Description.ofText("Hello");
        assertThat(description.blocks()).containsExactly(new TextBlock("Hello"));
    }

    @Test
    @DisplayName("the block list is an immutable copy, so callers cannot mutate a Description")
    void blocksAreDefensivelyCopied() {
        List<ContentBlock> source = new ArrayList<>();
        source.add(new TextBlock("one"));
        Description description = new Description(source);

        source.add(new TextBlock("two")); // must not affect the description

        assertThat(description.blocks()).hasSize(1);
        assertThatThrownBy(() -> description.blocks().add(new TextBlock("three")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullBlocks() {
        assertThatThrownBy(() -> new Description(null)).isInstanceOf(NullPointerException.class);
    }

    // --- plainTextPreview -------------------------------------------------------------------

    @Test
    void previewOfAnEmptyDescriptionIsEmpty() {
        assertThat(Description.empty().plainTextPreview(20)).isEmpty();
    }

    @Test
    void previewReturnsShortTextUnchanged() {
        assertThat(Description.ofText("Short").plainTextPreview(20)).isEqualTo("Short");
    }

    @Test
    @DisplayName("preview skips link and image blocks entirely")
    void previewSkipsNonTextBlocks() {
        Description description = new Description(List.of(
                new LinkBlock(SOMEWHERE, "A link"),
                new TextBlock("the text"),
                new ImageBlock(SOMEWHERE, "alt")));

        assertThat(description.plainTextPreview(50)).isEqualTo("the text");
    }

    @Test
    void previewJoinsMultipleTextBlocksWithASingleSpace() {
        Description description = new Description(List.of(
                new TextBlock("first"), new TextBlock("second")));

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
        // "🚀" is two chars (a surrogate pair); cutting between them would corrupt it.
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
}
