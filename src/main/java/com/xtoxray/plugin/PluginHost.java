package com.xtoxray.plugin;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything a Lua plugin can reach in the game. Implementations enforce the rules, not the script. */
public interface PluginHost {
    record Pos(int x, int y, int z) {
    }

    record EntityBox(String kind, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    /** Vanilla HUD parts a plugin may hide. */
    Set<String> HUD_PARTS = Set.of("hotbar", "health", "armor", "food", "air", "mount_health", "experience_bar", "experience_level",
        "crosshair", "effects", "boss_bar", "scoreboard", "chat", "player_list", "subtitles", "title", "overlay_message", "held_item_tooltip");

    /** Keys a plugin may hold down. */
    Set<String> CONTROLS = Set.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use");

    String blockAt(int x, int y, int z);

    double[] playerPosition();

    List<EntityBox> entities(double radius);

    void mine(List<Pos> blocks, String silkMode, int durabilityCost);

    void drawBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, boolean onTop);

    boolean xrayActive();

    Set<String> xrayBlocks();

    boolean xrayChange(String blockId, boolean add);

    void chat(String message);

    void log(String pluginId, String message);

    default int[] hudSize() { return new int[]{0, 0}; }
    default int textWidth(String text) { return 0; }
    default void hudText(String text, int x, int y, int color, boolean shadow) { }
    default void hudRect(int x1, int y1, int x2, int y2, int color) { }
    default boolean hudItem(String itemId, int x, int y) { return false; }

    default void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, int color, double width) { }
    default void drawText(String text, double x, double y, double z, int color, double scale) { }

    default Map<String, Object> playerInfo() { return null; }
    default List<Map<String, Object>> inventory() { return List.of(); }
    default Map<String, Object> lookingAt(double reach) { return null; }
    default Map<String, Object> worldInfo() { return null; }
    default Integer lightAt(int x, int y, int z) { return null; }
    default String biomeAt(int x, int y, int z) { return null; }
    default List<Map<String, Object>> entityList(double radius) { return List.of(); }

    default boolean setLook(double yaw, double pitch) { return false; }
    default boolean selectSlot(int slot) { return false; }
    default boolean setControl(String control, boolean down) { return false; }
    default void releaseControls() { }

    default boolean keyDown(String key) { return false; }
    default boolean playSound(String id, double volume, double pitch) { return false; }
    default void actionBar(String text) { }
    default void title(String title, String subtitle) { }

    default Object option(String name) { return null; }
    default boolean setOption(String name, Object value) { return false; }

    default boolean xraySetActive(boolean active) { return false; }
    default boolean xrayNightVision() { return false; }
    default void xraySetNightVision(boolean on) { }
    default int xrayRenderDistance() { return 0; }
    default void xraySetRenderDistance(int distance) { }
}
