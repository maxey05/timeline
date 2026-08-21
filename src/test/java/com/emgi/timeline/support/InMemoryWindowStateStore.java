package com.emgi.timeline.support;

import com.emgi.timeline.settings.WindowState;
import com.emgi.timeline.settings.WindowStateStore;
import java.util.Optional;

public final class InMemoryWindowStateStore implements WindowStateStore {

    private WindowState state;
    private int saveCount;

    public InMemoryWindowStateStore() {
        this(null);
    }

    public InMemoryWindowStateStore(WindowState initial) {
        this.state = initial;
    }

    @Override
    public Optional<WindowState> load() {
        return Optional.ofNullable(state).filter(WindowState::isUsable);
    }

    @Override
    public void save(WindowState state) {
        saveCount++;
        if (state.isUsable()) {
            this.state = state;
        }
    }

    public int saveCount() {
        return saveCount;
    }
}
