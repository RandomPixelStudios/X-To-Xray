package com.xtoxray.api.plugin;

import java.util.Optional;

public enum PluginPermission {
    STORAGE("storage", "Save its own data files"),
    XRAY_READ("xray.read", "See the Xray status and whitelist"),
    XRAY_WHITELIST("xray.whitelist", "Change the Xray whitelist");

    private final String key;
    private final String description;

    PluginPermission(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }

    public static Optional<PluginPermission> byKey(String key) {
        for (PluginPermission p : values()) {
            if (p.key.equals(key)) return Optional.of(p);
        }
        return Optional.empty();
    }
}
