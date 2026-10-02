package com.xtoxray.plugin;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.PluginDescriptor;

import java.net.URL;
import java.net.URLClassLoader;

public class ExternalPlugin {
    private final PluginDescriptor descriptor;
    private final Plugin instance;
    private final URLClassLoader classLoader;
    private final java.nio.file.Path sourceFile;
    private boolean enabled;

    public ExternalPlugin(PluginDescriptor descriptor, Plugin instance, URLClassLoader classLoader, java.nio.file.Path sourceFile) {
        this.descriptor = descriptor;
        this.instance = instance;
        this.classLoader = classLoader;
        this.sourceFile = sourceFile;
        this.enabled = false;
    }

    public PluginDescriptor getDescriptor() { return descriptor; }
    public Plugin getInstance() { return instance; }
    public java.nio.file.Path getSourceFile() { return sourceFile; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void close() {
        if (classLoader != null) {
            try { classLoader.close(); } catch (Exception e) { }
        }
    }
}