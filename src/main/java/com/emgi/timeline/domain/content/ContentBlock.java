package com.emgi.timeline.domain.content;

public sealed interface ContentBlock permits TextBlock, LinkBlock, ImageBlock {
}
