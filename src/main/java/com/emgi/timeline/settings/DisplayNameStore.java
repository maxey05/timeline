package com.emgi.timeline.settings;

import java.util.Optional;

/**
 * The name Timeline greets the user by, and where it is kept between launches.
 *
 * <p>Framework-free on purpose, exactly like {@link WindowStateStore}: the header label is a
 * view concern, but "what is this person called" is a setting, and settings do not import a
 * toolkit.</p>
 */
public interface DisplayNameStore
{
    /**
     * The longest name that will be stored. The greeting shares the header row with the New
     * Idea button, and a name past about this length starts squeezing it.
     */
    int MAX_LENGTH = 40;

    /** The stored name, or empty when the user has never given one. */
    Optional<String> load();

    /**
     * Stores {@code name} in normalized form. A name that normalizes to nothing is not
     * stored at all, so a blank answer leaves the application exactly as it was.
     */
    void save(String name);

    /**
     * Tidies a name typed by a human into the one form that is ever stored or displayed:
     * runs of whitespace collapsed to single spaces, trimmed at both ends, and cut to
     * {@link #MAX_LENGTH}.
     *
     * <p>Returns empty for null, for blank, and for anything that is only whitespace --
     * every caller treats those three the same way, which is why none of them is an error.
     * The cut is nudged back off a high surrogate so a name ending in an astral character
     * cannot be sliced in half.</p>
     */
    static Optional<String> normalize(String raw)
    {
        if(raw == null)
        {
            return Optional.empty();
        }

        String collapsed = raw.replaceAll("\\s+", " ").strip();

        if(collapsed.isEmpty())
        {
            return Optional.empty();
        }

        if(collapsed.length() > MAX_LENGTH)
        {
            int cut = Character.isHighSurrogate(collapsed.charAt(MAX_LENGTH - 1))
                ? MAX_LENGTH - 1
                : MAX_LENGTH;

            collapsed = collapsed.substring(0, cut).stripTrailing();
        }

        return Optional.of(collapsed);
    }
}
