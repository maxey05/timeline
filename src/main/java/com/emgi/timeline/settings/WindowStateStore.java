package com.emgi.timeline.settings;

import java.util.Optional;

public interface WindowStateStore
{
    Optional<WindowState> load();

    void save(WindowState state);
}
