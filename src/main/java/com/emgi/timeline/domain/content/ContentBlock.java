package com.emgi.timeline.domain.content;

/**
 * One element of an idea's description. Sealed with exactly three permitted variants
 * (locked decision #7 — video was dropped; a video link is just a {@link LinkBlock}).
 *
 * <p>Sealing is what makes extension safe: every {@code switch} over a {@code ContentBlock} is
 * exhaustive without a {@code default} branch, so adding a fourth variant later turns every place
 * that must handle it into a compile error rather than a silent runtime gap
 * (ARCHITECTURE.md §4.4, §7.5).
 */
public sealed interface ContentBlock permits TextBlock, LinkBlock, ImageBlock {
}
