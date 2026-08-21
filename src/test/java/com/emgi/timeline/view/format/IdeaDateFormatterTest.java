package com.emgi.timeline.view.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.support.FixedClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IdeaDateFormatter")
class IdeaDateFormatterTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    private FixedClock clock;
    private IdeaDateFormatter formatter;

    @BeforeEach
    void setUp() {
        clock = FixedClock.at(NOW);
        formatter = new IdeaDateFormatter(clock);
    }

    private String ago(Duration elapsed) {
        return formatter.format(NOW.minus(elapsed));
    }

    @Test
    @DisplayName("the present moment reads as Just now")
    void nowIsJustNow() {
        assertThat(formatter.format(NOW)).isEqualTo("Just now");
    }

    @Test
    @DisplayName("anything under a minute old reads as Just now")
    void underOneMinuteIsJustNow() {
        assertThat(ago(Duration.ofSeconds(59))).isEqualTo("Just now");
    }

    @Test
    @DisplayName("the minute boundary is singular, and stays singular until two minutes")
    void minuteBoundaryIsSingular() {
        assertThat(ago(Duration.ofSeconds(60))).isEqualTo("1 minute ago");
        assertThat(ago(Duration.ofSeconds(119))).isEqualTo("1 minute ago");
    }

    @Test
    @DisplayName("minutes are plural above one, and count up to 59")
    void minutesArePlural() {
        assertThat(ago(Duration.ofMinutes(5))).isEqualTo("5 minutes ago");
        assertThat(ago(Duration.ofMinutes(59))).isEqualTo("59 minutes ago");
    }

    @Test
    @DisplayName("the hour boundary switches units")
    void hourBoundary() {
        assertThat(ago(Duration.ofMinutes(60))).isEqualTo("1 hour ago");
        assertThat(ago(Duration.ofMinutes(1439))).isEqualTo("23 hours ago");
    }

    @Test
    @DisplayName("the day boundary switches units")
    void dayBoundary() {
        assertThat(ago(Duration.ofHours(24))).isEqualTo("1 day ago");
        assertThat(ago(Duration.ofHours(167))).isEqualTo("6 days ago");
    }

    @Test
    @DisplayName("at exactly seven days the relative form gives way to a date")
    void sevenDaysIsAbsolute() {
        assertThat(ago(IdeaDateFormatter.RELATIVE_LIMIT)).isEqualTo("Aug 13");
    }

    @Test
    @DisplayName("a date in the current year omits the year, and the day has no leading zero")
    void sameYearOmitsTheYear() {
        assertThat(formatter.format(Instant.parse("2026-08-03T09:15:00Z"))).isEqualTo("Aug 3");
    }

    @Test
    @DisplayName("a date in an earlier year carries the year")
    void earlierYearCarriesTheYear() {
        assertThat(formatter.format(Instant.parse("2025-08-03T09:15:00Z")))
                .isEqualTo("Aug 3, 2025");
    }

    @Test
    @DisplayName("a timestamp in the future reads as Just now, never as a negative age")
    void futureInstantsReadAsJustNow() {
        assertThat(formatter.format(NOW.plus(Duration.ofHours(1)))).isEqualTo("Just now");
        assertThat(formatter.format(NOW.plus(Duration.ofDays(30)))).isEqualTo("Just now");
    }

    @Test
    @DisplayName("absolute dates are rendered in the clock's zone, not UTC")
    void absoluteDatesUseTheClockZone() {
        Clock taipei = FixedClock.at(NOW).withZone(ZoneId.of("Asia/Taipei"));

        Instant instant = Instant.parse("2026-08-03T16:30:00Z");

        assertThat(new IdeaDateFormatter(taipei).format(instant)).isEqualTo("Aug 4");
        assertThat(formatter.format(instant)).isEqualTo("Aug 3");
    }

    @Test
    @DisplayName("the formatter re-reads the clock on every call")
    void readsTheClockEveryTime() {
        Instant instant = NOW.minus(Duration.ofMinutes(30));

        assertThat(formatter.format(instant)).isEqualTo("30 minutes ago");

        clock.advance(Duration.ofHours(2));

        assertThat(formatter.format(instant)).isEqualTo("2 hours ago");
    }

    @Test
    @DisplayName("a null instant is a programming error, not a blank label")
    void rejectsNullInstant() {
        assertThatThrownBy(() -> formatter.format(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a null clock is rejected at construction")
    void rejectsNullClock() {
        assertThatThrownBy(() -> new IdeaDateFormatter(null))
                .isInstanceOf(NullPointerException.class);
    }
}
