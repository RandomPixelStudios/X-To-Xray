package com.xtoxray.api.plugin;

import java.util.Set;

public final class PluginDescriptor {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final String mainClass;
    private final int apiVersion;
    private final Set<PluginPermission> permissions;

    public PluginDescriptor(String id, String name, String version, String description, String author,
                            String mainClass, int apiVersion, Set<PluginPermission> permissions) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.mainClass = mainClass;
        this.apiVersion = apiVersion;
        this.permissions = Set.copyOf(permissions);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getMainClass() { return mainClass; }
    public int getApiVersion() { return apiVersion; }
    public Set<PluginPermission> getPermissions() { return permissions; }
}
