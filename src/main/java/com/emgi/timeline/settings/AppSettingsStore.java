package com.emgi.timeline.settings;

public interface AppSettingsStore 
{
    AppSettings load();

    void save(AppSettings settings);
}
