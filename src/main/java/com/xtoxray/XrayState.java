package com.xtoxray;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class XrayState {
    private static final int DEFAULT_RENDER_DISTANCE = 64;
    private static final boolean DEFAULT_RENDER_WHILE_MOVING = true;
    private static final boolean DEFAULT_NIGHT_VISION = true;
    private static final Set<Block> DEFAULT_WHITELIST = new HashSet<>();

    static {
        DEFAULT_WHITELIST.add(Blocks.COAL_ORE);
        DEFAULT_WHITELIST.add(Blocks.DEEPSLATE_COAL_ORE);
        DEFAULT_WHITELIST.add(Blocks.IRON_ORE);
        DEFAULT_WHITELIST.add(Blocks.DEEPSLATE_IRON_ORE);
        DEFAULT_WHITELIST.add(Blocks.COPPER_ORE);
        DEFAULT_WHITELIST.add(Blocks.DEEPSLATE_COPPER_ORE);
        DEFAULT_WHITELIST.add(Blocks.GOLD_ORE);
        DEFAULT_WHITELIST.add(Blocks.DEEPSLATE_GOLD_ORE);
        DEFAULT_WHITELIST.add(Blocks.REDSTONE_ORE);
        DEFAULT_WHITELIST.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        DEFAULT_WHITELIST.add(Blocks.EMERALD_ORE);
        DEFAULT_WHITELIST.add(Blocks.DEEPSLATE_EMERALD_ORE);
        DEFAULT_WHITELIST.add(Blocks.LAPIS_ORE);
        DEFAULT_WHITELIST.add(Blocks.DEEPSLATE_LAPIS_ORE);
        DEFAULT_WHITELIST.add(Blocks.DIAMOND_ORE);
        DEFAULT_WHITELIST.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        DEFAULT_WHITELIST.add(Blocks.NETHER_GOLD_ORE);
        DEFAULT_WHITELIST.add(Blocks.NETHER_QUARTZ_ORE);
        DEFAULT_WHITELIST.add(Blocks.ANCIENT_DEBRIS);
        DEFAULT_WHITELIST.add(Blocks.CHEST);
        DEFAULT_WHITELIST.add(Blocks.TRAPPED_CHEST);
        DEFAULT_WHITELIST.add(Blocks.SPAWNER);
        DEFAULT_WHITELIST.add(Blocks.WATER);
        DEFAULT_WHITELIST.add(Blocks.LAVA);
    }

    private static final XrayState INSTANCE = new XrayState();
    private boolean active;
    private int renderDistance = DEFAULT_RENDER_DISTANCE;
    private boolean renderWhileMoving = DEFAULT_RENDER_WHILE_MOVING;
    private boolean nightVision = DEFAULT_NIGHT_VISION;
    private boolean renderingEnabled = true;
    private final Set<Block> whitelist = new HashSet<>(DEFAULT_WHITELIST);

    private XrayState() {
    }

    public static XrayState getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public void toggle() {
        active = !active;
        if (!active) {
            renderingEnabled = true;
        }
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public void setRenderDistance(int renderDistance) {
        this.renderDistance = Math.max(1, Math.min(64, renderDistance));
    }

    public boolean isRenderWhileMoving() {
        return renderWhileMoving;
    }

    public void setRenderWhileMoving(boolean renderWhileMoving) {
        this.renderWhileMoving = renderWhileMoving;
        if (renderWhileMoving) {
            this.renderingEnabled = true;
        }
    }

    public boolean isNightVision() {
        return nightVision;
    }

    public void setNightVision(boolean nightVision) {
        this.nightVision = nightVision;
    }

    public boolean isRenderingEnabled() {
        return renderingEnabled;
    }

    public void setRenderingEnabled(boolean renderingEnabled) {
        this.renderingEnabled = renderingEnabled;
    }

    public boolean isWhitelisted(Block block) {
        return whitelist.contains(block);
    }

    public Set<Block> getWhitelist() {
        return whitelist;
    }

    public void addToWhitelist(Block block) {
        whitelist.add(block);
    }

    public void removeFromWhitelist(Block block) {
        whitelist.remove(block);
    }

    public void resetToDefault() {
        this.renderDistance = DEFAULT_RENDER_DISTANCE;
        this.renderWhileMoving = DEFAULT_RENDER_WHILE_MOVING;
        this.nightVision = DEFAULT_NIGHT_VISION;
        this.whitelist.clear();
        this.whitelist.addAll(DEFAULT_WHITELIST);
    }
}
