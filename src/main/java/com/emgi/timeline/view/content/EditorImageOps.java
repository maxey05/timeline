package com.emgi.timeline.view.content;

import org.fxmisc.richtext.model.SegmentOps;

import java.util.Optional;

public final class EditorImageOps<S> implements SegmentOps<EditorImage, S>
{
    @Override
    public int length(EditorImage segment)
    {
        return segment.isEmpty() ? 0 : 1;
    }

    @Override
    public char charAt(EditorImage segment, int index)
    {
        return segment.isEmpty() ? ' ' : EditorImage.PLACEHOLDER;
    }

    @Override
    public String getText(EditorImage segment)
    {
        return segment.isEmpty() ? "" : String.valueOf(EditorImage.PLACEHOLDER);
    }

    @Override
    public EditorImage subSequence(EditorImage segment, int start, int end)
    {
        return start == 0 && end == 1 ? segment : EditorImage.EMPTY;
    }

    @Override
    public EditorImage subSequence(EditorImage segment, int start)
    {
        return start == 0 ? segment : EditorImage.EMPTY;
    }

    @Override
    public Optional<EditorImage> joinSeg(EditorImage current, EditorImage next)
    {
        return Optional.empty();
    }

    @Override
    public EditorImage createEmptySeg()
    {
        return EditorImage.EMPTY;
    }
}
