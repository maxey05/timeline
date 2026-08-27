package com.emgi.timeline.settings;

import java.util.Objects;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class SettingsController 
{
    private final AppSettingsStore store;

    private final StringProperty displayNameInput = new SimpleStringProperty(this, "displayNameInput", "");

    private final BooleanProperty darkTheme = new SimpleBooleanProperty(this, "darkTheme", true);

    private final BooleanProperty animationsEnabled =
        new SimpleBooleanProperty(this, "animationsEnabled", true);

    private final ReadOnlyObjectWrapper<AppSettings> saved =
        new ReadOnlyObjectWrapper<>(this, "saved", AppSettings.DEFAULTS);

    private final BooleanBinding dirty;

    public SettingsController(AppSettingsStore store)
    {
        this.store = Objects.requireNonNull(store, "store");

        this.dirty = Bindings.createBooleanBinding(
            () -> !pending().equals(saved.get()),
            displayNameInput, darkTheme, animationsEnabled, saved);

        reload();
    }

    public void reload()
    {
        saved.set(store.load());
        revert();
    }

    public void revert()
    {
        AppSettings current = saved.get();

        displayNameInput.set("");
        darkTheme.set(current.darkTheme());
        animationsEnabled.set(current.animationsEnabled());
    }

    public AppSettings pending()
    {
        return new AppSettings(effectiveDisplayName(), darkTheme.get(), animationsEnabled.get());
    }

    public boolean save()
    {
        if(!dirty.get())
        {
            return false;
        }

        AppSettings next = pending();

        store.save(next);
        saved.set(next);
        displayNameInput.set("");

        return true;
    }

    private String effectiveDisplayName()
    {
        return DisplayNameStore.normalize(displayNameInput.get())
            .orElseGet(() -> saved.get().displayName());
    }

    public StringProperty displayNameInputProperty()
    {
        return displayNameInput;
    }

    public BooleanProperty darkThemeProperty()
    {
        return darkTheme;
    }

    public BooleanProperty animationsEnabledProperty()
    {
        return animationsEnabled;
    }

    public ReadOnlyObjectProperty<AppSettings> savedProperty()
    {
        return saved.getReadOnlyProperty();
    }

    public AppSettings saved()
    {
        return saved.get();
    }

    public BooleanBinding dirtyBinding()
    {
        return dirty;
    }

    public boolean isDirty()
    {
        return dirty.get();
    }
}
