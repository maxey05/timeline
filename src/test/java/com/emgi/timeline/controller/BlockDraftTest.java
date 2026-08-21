package com.emgi.timeline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
import com.emgi.timeline.domain.content.TextBlock;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BlockDraft")
class BlockDraftTest {

    @Test
    @DisplayName("ofKind gives a row of that kind with every field blank")
    void ofKindStartsBlank() {
        BlockDraft draft = BlockDraft.ofKind(BlockKind.LINK);

        assertThat(draft.kind()).isEqualTo(BlockKind.LINK);
        assertThat(draft.textProperty().get()).isEmpty();
        assertThat(draft.uriProperty().get()).isEmpty();
        assertThat(draft.labelProperty().get()).isEmpty();
        assertThat(draft.altTextProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("ofKind and from reject null")
    void rejectsNull() {
        assertThatThrownBy(() -> BlockDraft.ofKind(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BlockDraft.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("from(TextBlock) carries the text across")
    void fromTextBlock() {
        BlockDraft draft = BlockDraft.from(new TextBlock("A cleaner approach."));

        assertThat(draft.kind()).isEqualTo(BlockKind.TEXT);
        assertThat(draft.textProperty().get()).isEqualTo("A cleaner approach.");
    }

    @Test
    @DisplayName("from(LinkBlock) carries the target and the label")
    void fromLinkBlock() {
        BlockDraft draft = BlockDraft.from(
                new LinkBlock(URI.create("https://example.com/x"), "The write-up"));

        assertThat(draft.kind()).isEqualTo(BlockKind.LINK);
        assertThat(draft.uriProperty().get()).isEqualTo("https://example.com/x");
        assertThat(draft.labelProperty().get()).isEqualTo("The write-up");
    }

    @Test
    @DisplayName("a link with no label of its own arrives showing its URL, never blank")
    void fromLinkBlockWithoutALabel() {
        BlockDraft draft = BlockDraft.from(LinkBlock.of(URI.create("https://example.com/x")));

        assertThat(draft.labelProperty().get()).isEqualTo("https://example.com/x");
    }

    @Test
    @DisplayName("from(ImageBlock) carries the source and the alt text")
    void fromImageBlock() {
        BlockDraft draft = BlockDraft.from(
                new ImageBlock(URI.create("file:///C:/pics/a.png"), "A screenshot"));

        assertThat(draft.kind()).isEqualTo(BlockKind.IMAGE);
        assertThat(draft.uriProperty().get()).isEqualTo("file:///C:/pics/a.png");
        assertThat(draft.altTextProperty().get()).isEqualTo("A screenshot");
    }

    @Test
    @DisplayName("an image with no alt text arrives blank, not null")
    void fromImageBlockWithoutAltText() {
        BlockDraft draft = BlockDraft.from(ImageBlock.of(URI.create("file:///C:/pics/a.png")));

        assertThat(draft.altTextProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("a fresh row is blank")
    void freshRowIsBlank() {
        assertThat(BlockDraft.ofKind(BlockKind.TEXT).isBlank()).isTrue();
    }

    @Test
    @DisplayName("whitespace is still blank — a row holding only spaces was never filled in")
    void whitespaceIsBlank() {
        BlockDraft draft = BlockDraft.ofKind(BlockKind.TEXT);
        draft.textProperty().set("   \n\t ");

        assertThat(draft.isBlank()).isTrue();
    }

    @Test
    @DisplayName("a row with only an alt text is not blank — the user meant something by it")
    void altTextAloneIsNotBlank() {
        BlockDraft draft = BlockDraft.ofKind(BlockKind.IMAGE);
        draft.altTextProperty().set("A screenshot");

        assertThat(draft.isBlank()).isFalse();
    }

    @Test
    @DisplayName("a text row never needs an address, whatever is in its other fields")
    void textRowNeverMissesAnAddress() {
        BlockDraft draft = BlockDraft.ofKind(BlockKind.TEXT);
        draft.textProperty().set("body");

        assertThat(draft.isMissingUri()).isFalse();
    }

    @Test
    @DisplayName("a link row with a whitespace-only address is missing its address")
    void linkRowWithBlankAddress() {
        BlockDraft draft = BlockDraft.ofKind(BlockKind.LINK);
        draft.uriProperty().set("   ");
        draft.labelProperty().set("The write-up");

        assertThat(draft.isMissingUri()).isTrue();
    }
}
