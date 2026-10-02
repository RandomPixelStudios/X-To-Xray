package com.xtoxray.plugin;

import java.util.ArrayList;
import java.util.List;

public class PluginState {
    private static final PluginState INSTANCE = new PluginState();
    private final List<PluginData> plugins = new ArrayList<>();

    private PluginState() {
        plugins.add(new PluginData("Veinminer", "Mine Whole Ore-Veins At Once!", "Random Pixel Studios"));
        plugins.add(new PluginData("Hitboxes", "View Hitboxes!", "Random Pixel Studios"));
    }

    public static PluginState getInstance() {
        return INSTANCE;
    }

    public List<PluginData> getPlugins() {
        return plugins;
    }

    public boolean isInstalled(String name) {
        for (PluginData p : plugins) {
            if (p.getName().equals(name)) return p.isInstalled();
        }
        return false;
    }

    public void install(String name) {
        for (PluginData p : plugins) {
            if (p.getName().equals(name)) {
                p.setInstalled(true);
                return;
            }
        }
    }

    public void uninstall(String name) {
        for (PluginData p : plugins) {
            if (p.getName().equals(name)) {
                p.setInstalled(false);
                return;
            }
        }
    }
}
