package com.emgi.timeline.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DisplayNameStoreTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n "})
    @DisplayName("nothing, blank and whitespace all normalize to no name")
    void emptyInputsNormalizeToNothing(String raw) {
        assertThat(DisplayNameStore.normalize(raw)).isEmpty();
    }

    @Test
    void aPlainNameSurvivesUnchanged() {
        assertThat(DisplayNameStore.normalize("Matthew")).contains("Matthew");
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed and inner runs collapse")
    void whitespaceIsTidied() {
        assertThat(DisplayNameStore.normalize("  Matthew   James \n")).contains("Matthew James");
    }

    @Test
    @DisplayName("a name longer than the cap is cut to it")
    void tooLongIsCut() {
        String longName = "a".repeat(DisplayNameStore.MAX_LENGTH + 10);

        Optional<String> normalized = DisplayNameStore.normalize(longName);

        assertThat(normalized).isPresent();
        assertThat(normalized.orElseThrow()).hasSize(DisplayNameStore.MAX_LENGTH);
    }

    @Test
    @DisplayName("the cut never leaves a dangling space")
    void cuttingDoesNotLeaveTrailingSpace() {
        String longName = "b".repeat(DisplayNameStore.MAX_LENGTH - 1) + " tail";

        assertThat(DisplayNameStore.normalize(longName))
                .contains("b".repeat(DisplayNameStore.MAX_LENGTH - 1));
    }

    @Test
    @DisplayName("the cut never splits a surrogate pair in half")
    void cuttingDoesNotSplitASurrogatePair() {
        String rocket = "\uD83D\uDE80";
        String longName = "c".repeat(DisplayNameStore.MAX_LENGTH - 1) + rocket;

        Optional<String> normalized = DisplayNameStore.normalize(longName);

        assertThat(normalized).isPresent();
        assertThat(Character.isHighSurrogate(
                normalized.orElseThrow().charAt(normalized.orElseThrow().length() - 1)))
                .isFalse();
    }
}
