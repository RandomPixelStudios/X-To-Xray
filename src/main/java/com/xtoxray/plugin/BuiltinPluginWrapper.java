package com.xtoxray.plugin;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;

public class BuiltinPluginWrapper implements Plugin {
    private final PluginData data;

    public BuiltinPluginWrapper(PluginData data) {
        this.data = data;
    }

    @Override
    public void onEnable(PluginContext context) { }
    @Override
    public void onDisable(PluginContext context) { }
    @Override
    public String getName() { return data.getName(); }
    @Override
    public String getDescription() { return data.getDescription(); }
    @Override
    public String getAuthor() { return "Random Pixel Studios"; }
}