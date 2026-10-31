package com.xtoxray.plugin;

import java.util.List;
import java.util.Set;

/** Everything a Lua plugin can reach in the game. Implementations enforce the rules, not the script. */
public interface PluginHost {
    record Pos(int x, int y, int z) {
    }

    record EntityBox(String kind, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

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
}
