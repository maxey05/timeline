package com.emgi.timeline.settings;

import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class PreferencesAppSettingsStore implements AppSettingsStore
{
    static final String NODE_PATH = PreferencesWindowStateStore.NODE_PATH;

    private static final String KEY_DARK_THEME = "ui.darkTheme";
    private static final String KEY_ANIMATIONS = "ui.animationsEnabled";

    private final Preferences node;

    private final DisplayNameStore displayNames;

    public PreferencesAppSettingsStore(Preferences node, DisplayNameStore displayNames)
    {
        this.node = Objects.requireNonNull(node, "node");
        this.displayNames = Objects.requireNonNull(displayNames, "displayNames");
    }

    public static PreferencesAppSettingsStore atUserNode()
    {
        Preferences node = Preferences.userRoot().node(NODE_PATH);

        return new PreferencesAppSettingsStore(node, new PreferencesDisplayNameStore(node));
    }

    @Override
    public AppSettings load()
    {
        return new AppSettings(
            displayNames.load().orElse(""),
            node.getBoolean(KEY_DARK_THEME, AppSettings.DEFAULTS.darkTheme()),
            node.getBoolean(KEY_ANIMATIONS, AppSettings.DEFAULTS.animationsEnabled()));
    }

    @Override
    public void save(AppSettings settings)
    {
        Objects.requireNonNull(settings, "settings");

        settings.name().ifPresent(displayNames::save);

        node.putBoolean(KEY_DARK_THEME, settings.darkTheme());
        node.putBoolean(KEY_ANIMATIONS, settings.animationsEnabled());

        try
        {
            node.flush();
        }
        catch(BackingStoreException e)
        {
            System.err.println("Timeline: could not save your settings - " + e.getMessage());
        }
    }
}
