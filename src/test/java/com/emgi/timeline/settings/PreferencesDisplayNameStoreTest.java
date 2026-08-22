package com.emgi.timeline.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreferencesDisplayNameStoreTest {

    private Preferences node;
    private PreferencesDisplayNameStore store;

    @BeforeEach
    void freshNode() {
        node = Preferences.userRoot().node(PreferencesDisplayNameStore.NODE_PATH + "/test");
        store = new PreferencesDisplayNameStore(node);
    }

    @AfterEach
    void removeNode() throws BackingStoreException {
        node.removeNode();
        node.flush();
    }

    @Test
    @DisplayName("a node with no name in it loads nothing")
    void anEmptyNodeLoadsNothing() {
        assertThat(store.load()).isEmpty();
    }

    @Test
    void aSavedNameComesBack() {
        store.save("Matthew");

        assertThat(store.load()).contains("Matthew");
    }

    @Test
    @DisplayName("a name is normalized on the way in")
    void aSavedNameIsNormalized() {
        store.save("   Matthew   James  ");

        assertThat(store.load()).contains("Matthew James");
    }

    @Test
    @DisplayName("a blank name is never written")
    void aBlankNameIsNotWritten() {
        store.save("   ");

        assertThat(store.load()).isEmpty();
    }

    @Test
    @DisplayName("a name hand-edited into the node to nothing loads as nothing")
    void garbageInTheNodeLoadsNothing() {
        store.save("Matthew");
        node.put("user.displayName", "   ");

        assertThat(store.load()).isEmpty();
    }
}
