package com.xtoxray.veinminer;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class VeinminerState {
    private static final VeinminerState INSTANCE = new VeinminerState();
    public static final int SILK_TOUCH_NEVER = 0;
    public static final int SILK_TOUCH_WITH_ENCHANTMENT = 1;
    public static final int SILK_TOUCH_ALWAYS = 2;

    private boolean enabled = true;
    private int maxBlocks = 10;
    private int silkTouchMode = SILK_TOUCH_WITH_ENCHANTMENT;
    private int durabilityCost = 1;
    private final Set<Block> whitelist = new HashSet<>();

    private VeinminerState() {
        whitelist.add(Blocks.COAL_ORE);
        whitelist.add(Blocks.DEEPSLATE_COAL_ORE);
        whitelist.add(Blocks.IRON_ORE);
        whitelist.add(Blocks.DEEPSLATE_IRON_ORE);
        whitelist.add(Blocks.COPPER_ORE);
        whitelist.add(Blocks.DEEPSLATE_COPPER_ORE);
        whitelist.add(Blocks.GOLD_ORE);
        whitelist.add(Blocks.DEEPSLATE_GOLD_ORE);
        whitelist.add(Blocks.REDSTONE_ORE);
        whitelist.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        whitelist.add(Blocks.EMERALD_ORE);
        whitelist.add(Blocks.DEEPSLATE_EMERALD_ORE);
        whitelist.add(Blocks.LAPIS_ORE);
        whitelist.add(Blocks.DEEPSLATE_LAPIS_ORE);
        whitelist.add(Blocks.DIAMOND_ORE);
        whitelist.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        whitelist.add(Blocks.NETHER_GOLD_ORE);
        whitelist.add(Blocks.NETHER_QUARTZ_ORE);
        whitelist.add(Blocks.ANCIENT_DEBRIS);
    }

    public static VeinminerState getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = Math.max(1, Math.min(64, maxBlocks));
    }

    public int getSilkTouchMode() {
        return silkTouchMode;
    }

    public void setSilkTouchMode(int silkTouchMode) {
        this.silkTouchMode = silkTouchMode;
    }

    public int getDurabilityCost() {
        return durabilityCost;
    }

    public void setDurabilityCost(int durabilityCost) {
        this.durabilityCost = Math.max(0, Math.min(10, durabilityCost));
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

    public String getSilkTouchModeName() {
        return switch (silkTouchMode) {
            case SILK_TOUCH_NEVER -> "Never Use Silk Touch";
            case SILK_TOUCH_ALWAYS -> "Always Use Silk Touch";
            default -> "Silk Touch With Enchantment";
        };
    }

    public void cycleSilkTouchMode() {
        silkTouchMode = (silkTouchMode + 1) % 3;
    }

    public void resetToDefault() {
        this.enabled = true;
        this.maxBlocks = 10;
        this.silkTouchMode = SILK_TOUCH_WITH_ENCHANTMENT;
        this.durabilityCost = 1;
        this.whitelist.clear();
        this.whitelist.add(Blocks.COAL_ORE);
        this.whitelist.add(Blocks.DEEPSLATE_COAL_ORE);
        this.whitelist.add(Blocks.IRON_ORE);
        this.whitelist.add(Blocks.DEEPSLATE_IRON_ORE);
        this.whitelist.add(Blocks.COPPER_ORE);
        this.whitelist.add(Blocks.DEEPSLATE_COPPER_ORE);
        this.whitelist.add(Blocks.GOLD_ORE);
        this.whitelist.add(Blocks.DEEPSLATE_GOLD_ORE);
        this.whitelist.add(Blocks.REDSTONE_ORE);
        this.whitelist.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        this.whitelist.add(Blocks.EMERALD_ORE);
        this.whitelist.add(Blocks.DEEPSLATE_EMERALD_ORE);
        this.whitelist.add(Blocks.LAPIS_ORE);
        this.whitelist.add(Blocks.DEEPSLATE_LAPIS_ORE);
        this.whitelist.add(Blocks.DIAMOND_ORE);
        this.whitelist.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        this.whitelist.add(Blocks.NETHER_GOLD_ORE);
        this.whitelist.add(Blocks.NETHER_QUARTZ_ORE);
        this.whitelist.add(Blocks.ANCIENT_DEBRIS);
    }
}
