package com.emgi.timeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ImageStore")
class ImageStoreTest {

    private static final byte[] BYTES = "not really a png".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    private ImageStore store() {
        return new ImageStore(root.resolve("images"));
    }

    @Test
    @DisplayName("store writes the bytes and returns an address that points at them")
    void storeWritesTheBytes() throws IOException {
        URI address = store().store(BYTES, "png");

        assertThat(Files.readAllBytes(Path.of(address))).isEqualTo(BYTES);
    }

    @Test
    @DisplayName("the directory is created on first write, not on construction")
    void theDirectoryIsCreatedLazily() {
        ImageStore store = store();
        assertThat(Files.exists(root.resolve("images"))).isFalse();

        store.store(BYTES, "png");

        assertThat(Files.isDirectory(root.resolve("images"))).isTrue();
    }

    @Test
    @DisplayName("two saves of identical bytes are two separate files — nothing is overwritten")
    void everySaveGetsItsOwnName() throws IOException {
        ImageStore store = store();

        URI first = store.store(BYTES, "png");
        URI second = store.store(BYTES, "png");

        assertThat(first).isNotEqualTo(second);
        try (var entries = Files.list(root.resolve("images"))) {
            assertThat(entries.count()).isEqualTo(2L);
        }
    }

    @Test
    void storeKeepsTheExtensionItIsGiven() {
        assertThat(store().store(BYTES, "gif").toString()).endsWith(".gif");
    }

    @Test
    @DisplayName("a missing, odd, or dangerous extension falls back to png")
    void oddExtensionsFallBackToPng() {
        ImageStore store = store();

        for (String odd : List.of("", "  ", "../../evil", "PNG!", "waytoolongextension")) {
            assertThat(store.store(BYTES, odd).toString())
                    .as("extension [%s]", odd)
                    .endsWith("." + ImageStore.DEFAULT_EXTENSION);
        }

        assertThat(store.store(BYTES, null).toString())
                .endsWith("." + ImageStore.DEFAULT_EXTENSION);
    }

    @Test
    @DisplayName("an upper-case extension is kept, lower-cased")
    void extensionsAreLowerCased() {
        assertThat(store().store(BYTES, "JPG").toString()).endsWith(".jpg");
    }

    @Test
    @DisplayName("copyFrom copies the content and keeps the extension but not the name")
    void copyFromCopiesTheFile() throws IOException {
        Path source = root.resolve("holiday snap.JPEG");
        Files.write(source, BYTES);

        URI address = store().copyFrom(source);

        assertThat(Files.readAllBytes(Path.of(address))).isEqualTo(BYTES);
        assertThat(address.toString()).endsWith(".jpeg");
        assertThat(address.toString()).doesNotContain("holiday");
    }

    @Test
    @DisplayName("the original is left where it was — inserting an image never moves the user's file")
    void copyFromLeavesTheOriginalAlone() throws IOException {
        Path source = root.resolve("cat.png");
        Files.write(source, BYTES);

        store().copyFrom(source);

        assertThat(Files.exists(source)).isTrue();
    }

    @Test
    void copyFromAFileWithNoExtensionFallsBackToPng() throws IOException {
        Path source = root.resolve("screenshot");
        Files.write(source, BYTES);

        assertThat(store().copyFrom(source).toString())
                .endsWith("." + ImageStore.DEFAULT_EXTENSION);
    }

    @Test
    @DisplayName("a source that is not there fails as an IO problem, not a mystery")
    void copyFromAMissingFileFails() {
        assertThatThrownBy(() -> store().copyFrom(root.resolve("nope.png")))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new ImageStore(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store().store(null, "png"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store().copyFrom(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the default location sits beside the database, under the user's home")
    void theDefaultLocationIsBesideTheDatabase() {
        Path images = ImageStore.defaultImageDirectory();

        assertThat(images.getFileName().toString()).isEqualTo("images");
        assertThat(images.getParent().getFileName().toString()).isEqualTo(".timeline");
    }
}
