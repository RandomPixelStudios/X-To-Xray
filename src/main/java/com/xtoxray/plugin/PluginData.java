package com.xtoxray.plugin;

public class PluginData {
    private final String name;
    private final String description;
    private final String creator;
    private boolean installed;

    public PluginData(String name, String description, String creator) {
        this.name = name;
        this.description = description;
        this.creator = creator;
        this.installed = false;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCreator() {
        return creator;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }
}
