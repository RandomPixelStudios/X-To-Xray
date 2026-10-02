package com.xtoxray.api.plugin;

import java.nio.file.Path;

public interface PluginContext {
    PluginLogger getLogger();
    Path getDataFolder();
    com.xtoxray.api.plugin.event.EventBus getEventBus();
}