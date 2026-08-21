package com.emgi.timeline.settings;

import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class PreferencesWindowStateStore implements WindowStateStore
{
    static final String NODE_PATH = "com/emgi/timeline";

    private static final String KEY_X = "window.x";
    private static final String KEY_Y = "window.y";
    private static final String KEY_WIDTH = "window.width";
    private static final String KEY_HEIGHT = "window.height";
    private static final String KEY_MAXIMIZED = "window.maximized";

    private final Preferences node;

    public PreferencesWindowStateStore(Preferences node)
    {
        this.node = Objects.requireNonNull(node, "node");
    }

    public static PreferencesWindowStateStore atUserNode()
    {
        return new PreferencesWindowStateStore(Preferences.userRoot().node(NODE_PATH));
    }

    @Override
    public Optional<WindowState> load()
    {
        WindowState state = new WindowState(
            node.getDouble(KEY_X, Double.NaN),
            node.getDouble(KEY_Y, Double.NaN),
            node.getDouble(KEY_WIDTH, Double.NaN),
            node.getDouble(KEY_HEIGHT, Double.NaN),
            node.getBoolean(KEY_MAXIMIZED, false));

        return state.isUsable() ? Optional.of(state) : Optional.empty();
    }

    @Override
    public void save(WindowState state)
    {
        Objects.requireNonNull(state, "state");

        if(!state.isUsable())
        {
            return;
        }

        node.putDouble(KEY_X, state.x());
        node.putDouble(KEY_Y, state.y());
        node.putDouble(KEY_WIDTH, state.width());
        node.putDouble(KEY_HEIGHT, state.height());
        node.putBoolean(KEY_MAXIMIZED, state.maximized());

        try
        {
            node.flush();
        }
        catch(BackingStoreException e)
        {
            System.err.println("Timeline: could not save the window size — " + e.getMessage());
        }
    }
}
