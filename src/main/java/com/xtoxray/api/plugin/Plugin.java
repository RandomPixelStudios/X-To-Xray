package com.xtoxray.api.plugin;

import com.xtoxray.api.plugin.event.Listener;

/** Entry point of a plugin. Identity (id, name, author, ...) comes from plugin.yml. */
public interface Plugin extends Listener {
    int API_VERSION = 2;

    void onEnable(PluginContext context);

    default void onDisable(PluginContext context) {
    }

    /** Name of an optional tab in the XtoXray menu, or null for none. */
    default String getSidebarTabName() {
        return null;
    }

    default String[] getSidebarTabContent() {
        return new String[0];
    }
}
