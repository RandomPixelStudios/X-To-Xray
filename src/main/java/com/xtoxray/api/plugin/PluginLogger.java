package com.xtoxray.api.plugin;

public interface PluginLogger {
    void info(String message);
    void warn(String message);
    void error(String message);
}