package com.xtoxray.api.plugin;

public class PluginDescriptor {
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final String mainClass;

    public PluginDescriptor(String name, String version, String description, String author, String mainClass) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.mainClass = mainClass;
    }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getMainClass() { return mainClass; }
}
