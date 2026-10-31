package com.xtoxray.plugin;

import java.nio.file.Path;

public final class ExternalPlugin {
    public enum Status { NEEDS_APPROVAL, ENABLED, DISABLED, CRASHED, REVOKED }

    private final PluginDefinition definition;
    private final Path sourceFile;
    private Status status = Status.NEEDS_APPROVAL;
    private LuaPlugin runtime;

    ExternalPlugin(PluginDefinition definition, Path sourceFile) {
        this.definition = definition;
        this.sourceFile = sourceFile;
    }

    public PluginDefinition getDefinition() { return definition; }
    public Path getSourceFile() { return sourceFile; }
    public Status getStatus() { return status; }
    void setStatus(Status status) { this.status = status; }
    public LuaPlugin getRuntime() { return runtime; }
    void setRuntime(LuaPlugin runtime) { this.runtime = runtime; }
}
