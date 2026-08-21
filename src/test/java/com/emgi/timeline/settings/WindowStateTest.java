package com.emgi.timeline.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WindowStateTest {

    private static WindowState normal() {
        return new WindowState(100, 80, 900, 640, false);
    }

    private static final double SCREEN_X = 0;
    private static final double SCREEN_Y = 0;
    private static final double SCREEN_WIDTH = 1920;
    private static final double SCREEN_HEIGHT = 1040;

    private static boolean onPrimaryScreen(WindowState state) {
        return state.intersects(SCREEN_X, SCREEN_Y, SCREEN_WIDTH, SCREEN_HEIGHT);
    }

    @Test
    @DisplayName("an ordinary window is usable")
    void aNormalWindowIsUsable() {
        assertThat(normal().isUsable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "y", "width", "height"})
    @DisplayName("a NaN in any coordinate makes the state unusable")
    void nanInAnyCoordinateIsNotUsable(String field) {
        double nan = Double.NaN;
        WindowState state = switch (field) {
            case "x" -> new WindowState(nan, 80, 900, 640, false);
            case "y" -> new WindowState(100, nan, 900, 640, false);
            case "width" -> new WindowState(100, 80, nan, 640, false);
            default -> new WindowState(100, 80, 900, nan, false);
        };

        assertThat(state.isUsable()).as("NaN in %s", field).isFalse();
    }

    @Test
    @DisplayName("infinite coordinates are not usable")
    void infiniteCoordinatesAreNotUsable() {
        assertThat(new WindowState(Double.POSITIVE_INFINITY, 0, 900, 640, false).isUsable())
                .isFalse();
        assertThat(new WindowState(0, Double.NEGATIVE_INFINITY, 900, 640, false).isUsable())
                .isFalse();
    }

    @Test
    @DisplayName("a window narrower than the minimum usable size is rejected")
    void aWindowNarrowerThanMinUsableIsRejected() {
        assertThat(new WindowState(0, 0, WindowState.MIN_USABLE - 1, 640, false).isUsable())
                .isFalse();
    }

    @Test
    @DisplayName("a window shorter than the minimum usable size is rejected")
    void aWindowShorterThanMinUsableIsRejected() {
        assertThat(new WindowState(0, 0, 900, WindowState.MIN_USABLE - 1, false).isUsable())
                .isFalse();
    }

    @Test
    @DisplayName("a window exactly at the minimum usable size is accepted")
    void aWindowAtExactlyMinUsableIsAccepted() {
        assertThat(new WindowState(0, 0, WindowState.MIN_USABLE, WindowState.MIN_USABLE, false)
                .isUsable()).isTrue();
    }

    @Test
    @DisplayName("zero size is rejected")
    void zeroSizeIsRejected() {
        assertThat(new WindowState(0, 0, 0, 0, false).isUsable()).isFalse();
    }

    @Test
    @DisplayName("a negative origin is usable — that is a second monitor, not corruption")
    void aNegativeOriginIsUsable() {
        assertThat(new WindowState(-1800, -200, 900, 640, false).isUsable()).isTrue();
    }

    @Test
    @DisplayName("a window fully inside the screen intersects it")
    void aWindowFullyInsideTheScreenIntersectsIt() {
        assertThat(onPrimaryScreen(normal())).isTrue();
    }

    @Test
    @DisplayName("a window straddling the screen edge intersects it")
    void aWindowStraddlingTheScreenEdgeIntersectsIt() {
        assertThat(onPrimaryScreen(new WindowState(-400, 80, 900, 640, false))).isTrue();
        assertThat(onPrimaryScreen(new WindowState(1700, 80, 900, 640, false))).isTrue();
    }

    @Test
    @DisplayName("a window entirely off the left of the screen does not intersect it")
    void aWindowEntirelyOffTheLeftOfTheScreenDoesNotIntersect() {
        assertThat(onPrimaryScreen(new WindowState(-1000, 80, 900, 640, false))).isFalse();
    }

    @Test
    @DisplayName("a window entirely below the screen does not intersect it")
    void aWindowEntirelyBelowTheScreenDoesNotIntersect() {
        assertThat(onPrimaryScreen(new WindowState(100, 1100, 900, 640, false))).isFalse();
    }

    @Test
    @DisplayName("an overlap thinner than the visible minimum does not count, on either axis")
    void aWindowOverlappingByLessThanMinVisibleDoesNotIntersect() {
        double slivers = WindowState.MIN_VISIBLE - 1;

        assertThat(onPrimaryScreen(new WindowState(-900 + slivers, 80, 900, 640, false)))
                .as("horizontal sliver").isFalse();
        assertThat(onPrimaryScreen(new WindowState(100, -640 + slivers, 900, 640, false)))
                .as("vertical sliver").isFalse();
    }

    @Test
    @DisplayName("an overlap of exactly the visible minimum counts")
    void aWindowOverlappingByExactlyMinVisibleIntersects() {
        assertThat(onPrimaryScreen(
                new WindowState(-900 + WindowState.MIN_VISIBLE, 80, 900, 640, false))).isTrue();
    }

    @Test
    @DisplayName("an unusable state is visible nowhere")
    void anUnusableStateIntersectsNothing() {
        assertThat(onPrimaryScreen(new WindowState(Double.NaN, Double.NaN, 900, 640, false)))
                .isFalse();
    }

    @Test
    @DisplayName("maximized is carried but does not affect usability")
    void maximizedIsCarriedButDoesNotAffectUsability() {
        WindowState maximized = new WindowState(100, 80, 900, 640, true);

        assertThat(maximized.maximized()).isTrue();
        assertThat(maximized.isUsable()).isTrue();
        assertThat(normal().maximized()).isFalse();
    }
}
