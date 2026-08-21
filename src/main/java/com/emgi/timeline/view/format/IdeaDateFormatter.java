package com.emgi.timeline.view.format;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public final class IdeaDateFormatter
{
    public static final Duration RELATIVE_LIMIT = Duration.ofDays(7);

    private static final String JUST_NOW = "Just now";

    private static final DateTimeFormatter SAME_YEAR =
        DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter OTHER_YEAR =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private final Clock clock;

    public IdeaDateFormatter(Clock clock)
    {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public String format(Instant instant)
    {
        Objects.requireNonNull(instant, "instant");

        Instant now = clock.instant();
        Duration elapsed = Duration.between(instant, now);

        if(elapsed.isNegative() || elapsed.toMinutes() < 1)
        {
            return JUST_NOW;
        }

        if(elapsed.toHours() < 1)
        {
            return plural(elapsed.toMinutes(), "minute");
        }

        if(elapsed.toDays() < 1)
        {
            return plural(elapsed.toHours(), "hour");
        }

        if(elapsed.compareTo(RELATIVE_LIMIT) < 0)
        {
            return plural(elapsed.toDays(), "day");
        }

        ZonedDateTime zoned = instant.atZone(clock.getZone());
        ZonedDateTime nowZoned = now.atZone(clock.getZone());

        return zoned.getYear() == nowZoned.getYear()
            ? SAME_YEAR.format(zoned)
            : OTHER_YEAR.format(zoned);
    }

    private static String plural(long amount, String unit)
    {
        return amount + " " + unit + (amount == 1 ? "" : "s") + " ago";
    }
}
