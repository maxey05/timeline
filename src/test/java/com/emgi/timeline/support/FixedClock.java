package com.emgi.timeline.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * A clock that only moves when a test tells it to.
 *
 * <p>{@link Clock#fixed} would cover "what time is it", but Phase 2 needs {@code updatedAt} to be
 * strictly later than {@code createdAt} after an update, which means advancing time mid-test —
 * hence a small mutable clock rather than the JDK's immutable one.
 *
 * <p>Not thread-safe, and doesn't need to be: tests using it are single-threaded.
 */
public final class FixedClock extends Clock {

    /** An arbitrary, memorable instant to anchor tests to. */
    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    private final ZoneId zone;
    private Instant instant;

    private FixedClock(Instant instant, ZoneId zone) {
        this.instant = Objects.requireNonNull(instant, "instant");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public static FixedClock at(Instant instant) {
        return new FixedClock(instant, ZoneOffset.UTC);
    }

    /** e.g. {@code FixedClock.at("2026-03-04T10:15:30Z")}. */
    public static FixedClock at(String isoInstant) {
        return at(Instant.parse(isoInstant));
    }

    public static FixedClock atDefault() {
        return at(DEFAULT_INSTANT);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new FixedClock(instant, newZone);
    }

    /** Moves the clock forward and returns the new instant, for convenient assertions. */
    public Instant advance(Duration amount) {
        instant = instant.plus(amount);
        return instant;
    }

    public Instant advanceSeconds(long seconds) {
        return advance(Duration.ofSeconds(seconds));
    }

    public void set(Instant newInstant) {
        instant = Objects.requireNonNull(newInstant, "newInstant");
    }
}
