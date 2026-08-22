package com.emgi.timeline.domain.content;

/**
 * One piece of a parsed description.
 *
 * <p>Segments are <em>derived</em>, never stored. The stored form of a description is a
 * single string; {@link DescriptionParser} turns that string into segments every time the
 * detail panel needs to draw it. Nothing in the app holds a segment across an edit.
 */
public sealed interface DescriptionSegment permits ParagraphSegment, ImageSegment {
}
