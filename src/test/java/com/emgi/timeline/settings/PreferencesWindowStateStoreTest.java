package com.emgi.timeline.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreferencesWindowStateStoreTest {

    private Preferences node;
    private PreferencesWindowStateStore store;

    @BeforeEach
    void freshNode() {
        node = Preferences.userRoot().node(PreferencesWindowStateStore.NODE_PATH + "/test");
        store = new PreferencesWindowStateStore(node);
    }

    @AfterEach
    void removeNode() throws BackingStoreException {
        node.removeNode();
        node.flush();
    }

    @Test
    @DisplayName("an empty node loads nothing")
    void anEmptyNodeLoadsNothing() {
        assertThat(store.load()).isEmpty();
    }

    @Test
    @DisplayName("a saved state comes back")
    void aSavedStateComesBack() {
        WindowState saved = new WindowState(120, 60, 1024, 768, true);

        store.save(saved);

        Optional<WindowState> loaded = store.load();
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow()).isEqualTo(saved);
    }

    @Test
    @DisplayName("an unusable state is never written")
    void anUnusableStateIsNotWritten() {
        store.save(new WindowState(Double.NaN, Double.NaN, Double.NaN, Double.NaN, false));

        assertThat(store.load()).isEmpty();
    }

    @Test
    @DisplayName("garbage in the node loads nothing rather than throwing")
    void garbageInTheNodeLoadsNothing() {
        store.save(new WindowState(120, 60, 1024, 768, false));
        node.put("window.width", "banana");

        assertThat(store.load()).isEmpty();
    }

    @Test
    @DisplayName("saving twice keeps the second")
    void savingTwiceKeepsTheSecond() {
        store.save(new WindowState(10, 10, 800, 600, false));
        store.save(new WindowState(20, 20, 1000, 700, true));

        assertThat(store.load().orElseThrow()).isEqualTo(new WindowState(20, 20, 1000, 700, true));
    }
}
