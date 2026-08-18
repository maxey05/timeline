package com.emgi.timeline.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TagTest {

    @ParameterizedTest(name = "\"{0}\" normalizes to \"{1}\"")
    @CsvSource({
            "java,           java",
            "Java,           java",
            "JAVA,           java",
            "'  java  ',     java",
            "'machine   learning', machine learning",
            "'Machine\tLearning',  machine learning",
    })
    void normalizesToCanonicalForm(String raw, String expected) {
        assertThat(Tag.of(raw).name()).isEqualTo(expected);
    }

    @Test
    @DisplayName("normalization is idempotent, so a canonical name survives a second pass")
    void normalizationIsIdempotent() {
        Tag once = Tag.of("  Machine   Learning ");
        assertThat(Tag.of(once.name())).isEqualTo(once);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void rejectsBlank(String raw) {
        assertThatThrownBy(() -> Tag.of(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> Tag.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsATagAtTheLengthLimit() {
        String atLimit = "a".repeat(Tag.MAX_LENGTH);
        assertThat(Tag.of(atLimit).name()).hasSize(Tag.MAX_LENGTH);
    }

    @Test
    void rejectsATagOverTheLengthLimit() {
        String overLimit = "a".repeat(Tag.MAX_LENGTH + 1);
        assertThatThrownBy(() -> Tag.of(overLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(Tag.MAX_LENGTH));
    }

    @Test
    @DisplayName("the canonical constructor refuses a non-canonical name")
    void constructorRejectsNonCanonicalName() {
        assertThatThrownBy(() -> new Tag("Java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical");
    }

    @Test
    @DisplayName("tags differing only by case are the same tag and collapse in a Set")
    void tagsDifferingOnlyByCaseCollapse() {
        // HashSet, not Set.of — Set.of throws on duplicates instead of collapsing them,
        // which would mask the very behaviour under test.
        Set<Tag> tags = new HashSet<>(List.of(Tag.of("Java"), Tag.of("java"), Tag.of(" JAVA ")));
        assertThat(tags).hasSize(1);
        assertThat(Tag.of("Java")).isEqualTo(Tag.of("java"));
        assertThat(Tag.of("Java")).hasSameHashCodeAs(Tag.of("java"));
    }

    @Test
    @DisplayName("non-ASCII tags survive normalization intact")
    void supportsNonAsciiTags() {
        assertThat(Tag.of("  想法  ").name()).isEqualTo("想法");
        assertThat(Tag.of("Idée").name()).isEqualTo("idée");
        assertThat(Tag.of("🚀 rocket").name()).isEqualTo("🚀 rocket");
    }

    @Test
    void toStringIsTheName() {
        assertThat(Tag.of("Java")).hasToString("java");
    }
}
