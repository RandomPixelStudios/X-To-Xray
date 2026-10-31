package com.xtoxray.plugin;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginDescriptor;
import java.nio.file.Path;

public final class ExternalPlugin {
    public enum Status { NEEDS_APPROVAL, ENABLED, DISABLED, FAILED, REVOKED }

    private final PluginJar jar;
    private final Path sourceFile;
    private Status status = Status.DISABLED;
    private Plugin instance;
    private int errors;

    ExternalPlugin(PluginJar jar, Path sourceFile) {
        this.jar = jar;
        this.sourceFile = sourceFile;
    }

    public PluginDescriptor getDescriptor() { return jar.descriptor(); }
    public PluginJar getJar() { return jar; }
    public Path getSourceFile() { return sourceFile; }
    public Status getStatus() { return status; }
    void setStatus(Status status) { this.status = status; }
    Plugin getInstance() { return instance; }
    void setInstance(Plugin instance) { this.instance = instance; }
    int addError() { return ++errors; }
    void resetErrors() { errors = 0; }
}
