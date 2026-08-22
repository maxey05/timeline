package com.emgi.timeline.domain.content;

import java.net.URI;
import java.util.Objects;

public record TextRun(String text, URI target) {

    public TextRun {
        Objects.requireNonNull(text, "text");
    }

    public static TextRun plain(String text) {
        return new TextRun(text, null);
    }

    public static TextRun link(String text, URI target) {
        return new TextRun(text, Objects.requireNonNull(target, "target"));
    }

    public boolean isLink() {
        return target != null;
    }
}
