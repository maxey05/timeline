package com.emgi.timeline.controller;

import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
import com.emgi.timeline.domain.content.TextBlock;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

public final class BlockDraft
{
    private final BlockKind kind;

    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final StringProperty uri = new SimpleStringProperty(this, "uri", "");
    private final StringProperty label = new SimpleStringProperty(this, "label", "");
    private final StringProperty altText = new SimpleStringProperty(this, "altText", "");

    private BlockDraft(BlockKind kind)
    {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public static BlockDraft ofKind(BlockKind kind)
    {
        return new BlockDraft(kind);
    }

    public static BlockDraft from(ContentBlock block)
    {
        Objects.requireNonNull(block, "block");

        return switch(block)
        {
            case TextBlock textBlock ->
            {
                BlockDraft draft = new BlockDraft(BlockKind.TEXT);
                draft.text.set(textBlock.text());
                yield draft;
            }
            case LinkBlock linkBlock ->
            {
                BlockDraft draft = new BlockDraft(BlockKind.LINK);
                draft.uri.set(linkBlock.target().toString());
                draft.label.set(linkBlock.label());
                yield draft;
            }
            case ImageBlock imageBlock ->
            {
                BlockDraft draft = new BlockDraft(BlockKind.IMAGE);
                draft.uri.set(imageBlock.source().toString());
                draft.altText.set(imageBlock.altText());
                yield draft;
            }
        };
    }

    public BlockKind kind()
    {
        return kind;
    }

    public StringProperty textProperty()
    {
        return text;
    }

    public StringProperty uriProperty()
    {
        return uri;
    }

    public StringProperty labelProperty()
    {
        return label;
    }

    public StringProperty altTextProperty()
    {
        return altText;
    }

    public boolean isBlank()
    {
        return blank(text.get()) && blank(uri.get()) && blank(label.get()) && blank(altText.get());
    }

    public boolean isMissingUri()
    {
        return kind != BlockKind.TEXT && blank(uri.get());
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }
}
