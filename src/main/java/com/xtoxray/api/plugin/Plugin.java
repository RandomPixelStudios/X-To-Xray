package com.xtoxray.api.plugin;

import com.xtoxray.api.plugin.event.Listener;

public interface Plugin extends Listener {
    void onEnable(PluginContext context);
    void onDisable(PluginContext context);
    String getName();
    String getDescription();
    String getAuthor();
    default String getSidebarTabName() {
        return null;
    }
    default String[] getSidebarTabContent() {
        return new String[0];
    }
}