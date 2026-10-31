package com.xtoxray.api.plugin;

import java.util.Set;

/** Blocks are named by registry id, e.g. "minecraft:diamond_ore". */
public interface XrayAccess {
    boolean isActive();

    Set<String> getWhitelist();

    /** @return false if the block id does not exist */
    boolean addToWhitelist(String blockId);

    boolean removeFromWhitelist(String blockId);
}
