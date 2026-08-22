package com.emgi.timeline.settings;

import java.util.Optional;

public interface DisplayNameStore
{
    int MAX_LENGTH = 40;

    Optional<String> load();

    void save(String name);

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
