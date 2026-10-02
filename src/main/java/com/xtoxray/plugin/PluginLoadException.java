package com.xtoxray.plugin;

public class PluginLoadException extends Exception {
    public PluginLoadException(String message) { super(message); }
    public PluginLoadException(String message, Throwable cause) { super(message, cause); }
}