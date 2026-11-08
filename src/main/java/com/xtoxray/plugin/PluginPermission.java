package com.xtoxray.plugin;

public enum PluginPermission {
    STORAGE("storage", "Save its own data"),
    WORLD_READ("world.read", "See blocks and your position"),
    WORLD_MINE("world.mine", "Break blocks for you"),
    ENTITIES_READ("entities.read", "See nearby players and mobs"),
    RENDER("render", "Draw boxes in the world"),
    XRAY("xray", "See and change the Xray whitelist"),
    CHAT("chat", "Show messages in your chat and on screen"),
    CHAT_READ("chat.read", "Read and hide incoming chat messages"),
    HUD("hud", "Draw on your HUD and hide parts of it"),
    PLAYER_READ("player.read", "See your health, inventory and stats"),
    PLAYER_CONTROL("player.control", "Move your character and change your hotbar slot"),
    INPUT("input", "Notice keys you press while no menu is open"),
    SOUND("sound", "Play sounds"),
    OPTIONS("options", "Change game options like FOV and brightness");

    private final String key;
    private final String description;

    PluginPermission(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() { return key; }
    public String description() { return description; }

    static PluginPermission byKey(String key) throws PluginLoadException {
        for (PluginPermission p : values()) {
            if (p.key.equals(key)) return p;
        }
        throw new PluginLoadException("Unknown permission: " + PluginDefinition.clean(key));
    }
}
