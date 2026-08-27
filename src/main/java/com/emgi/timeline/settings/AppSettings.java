package com.emgi.timeline.settings;

import java.util.Optional;

public record AppSettings(String displayName, boolean darkTheme, boolean animationsEnabled) 
{
    public static final AppSettings DEFAULTS = new AppSettings("", true, true);

    public AppSettings
    {
        displayName = DisplayNameStore.normalize(displayName).orElse("");
    }

    public Optional<String> name()
    {
        return displayName.isEmpty() ? Optional.empty() : Optional.of(displayName);
    }

    public AppSettings withDisplayName(String value)
    {
        return new AppSettings(value, darkTheme, animationsEnabled);
    }

    public AppSettings withDarkTheme(boolean value)
    {
        return new AppSettings(displayName, value, animationsEnabled);
    }

    public AppSettings withAnimationsEnabled(boolean value)
    {
        return new AppSettings(displayName, darkTheme, value);
    }
}
