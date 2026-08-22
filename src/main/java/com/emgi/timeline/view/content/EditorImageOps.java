package com.emgi.timeline.view.content;

import org.fxmisc.richtext.model.SegmentOps;

import java.util.Optional;

/**
 * Teaches RichTextFX how to treat an {@link EditorImage} as a piece of text.
 *
 * <p>RichTextFX does not know what a segment is. It only knows how to ask questions about
 * one: how long are you, what character is at index n, give me the part of you between
 * these two offsets. Answer those and every text operation the user already knows --
 * clicking, selecting, Backspace, undo, cut and paste -- works on a picture for free,
 * because none of those operations ever needed to know what the segment really was.
 *
 * <p>Every answer here follows from a single decision: <strong>an image is exactly one
 * character long</strong>. So the length is 1, the character is the object replacement
 * character, and there is no such thing as half an image -- any subSequence that does not
 * cover the whole of that one character is the empty segment.
 *
 * <p>joinSeg always refuses. Two adjacent pictures are two pictures; merging them into one
 * segment would be merging two characters into one, which is exactly as wrong as it sounds.
 *
 * @param <S> the text style type, which images ignore entirely. It is a parameter only
 *            because RichTextFX styles every segment uniformly, and an image has nothing
 *            a text style could apply to.
 */
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
