package com.emgi.timeline.settings;

import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Keeps the display name in the same {@code Preferences} node the window geometry uses.
 *
 * <p>That is a deliberate reuse rather than a coincidence: everything that is a setting of
 * the application rather than a piece of the user's data lives in one place, and the ideas
 * database stays a database of ideas.</p>
 */
public final class PreferencesDisplayNameStore implements DisplayNameStore
{
    /** The same node as the window state. Sharing the constant keeps them from drifting. */
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

    /**
     * Normalizes on the way out as well as on the way in: the node is a text file on somebody's
     * disk, and a name that was hand-edited into it is still not allowed to be blank or endless.
     */
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
