package com.emgi.timeline.settings;

import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class PreferencesDisplayNameStore implements DisplayNameStore
{
    static final String NODE_PATH = PreferencesWindowStateStore.NODE_PATH;

    private static final String KEY_NAME = "user.displayName";

    private final Preferences node;

    public PreferencesDisplayNameStore(Preferences node)
    {
        this.node = Objects.requireNonNull(node, "node");
    }

    public static PreferencesDisplayNameStore atUserNode()
    {
        return new PreferencesDisplayNameStore(Preferences.userRoot().node(NODE_PATH));
    }

    @Override
    public Optional<String> load()
    {
        return DisplayNameStore.normalize(node.get(KEY_NAME, null));
    }

    @Override
    public void save(String name)
    {
        Optional<String> normalized = DisplayNameStore.normalize(name);

        if(normalized.isEmpty())
        {
            return;
        }

        node.put(KEY_NAME, normalized.get());

        try
        {
            node.flush();
        }
        catch(BackingStoreException e)
        {
            System.err.println("Timeline: could not save your name - " + e.getMessage());
        }
    }
}
