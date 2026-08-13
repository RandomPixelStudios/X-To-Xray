/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.xtoxray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xtoxray.XtoXray;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class XrayState {
    private static final XrayState INSTANCE = new XrayState();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private boolean active;
    private boolean serverAllowsXray;
    private final Set<Block> whitelist = new HashSet<Block>();
    private final Set<Block> disabledBlocks = new HashSet<Block>();
    private final Set<Block> veinMinerWhitelist = new HashSet<Block>();
    private final Set<Block> containerWhitelist = new HashSet<Block>();
    private final Set<String> hitboxEntityWhitelist = new HashSet<String>();
    private final Set<String> installedPlugins = new HashSet<String>();
    private boolean veinMiner;
    private boolean veinMinerFortune = true;
    private boolean veinMinerSilkTouch;
    private int oreRenderDistance = 128;
    private boolean showHitboxes;
    private boolean showContainerView = true;
    private String shownChangelogVersion = "";
    private boolean neverShowChangelog;
    private int veinMinerDurability = 1;

    private XrayState() {
    }

    public static XrayState getInstance() {
        return INSTANCE;
    }

    public void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("xtoxray.json");
        if (!Files.exists(path, new LinkOption[0])) {
            this.initDefaults();
            this.save();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path);){
            ConfigData data = (ConfigData)GSON.fromJson((Reader)reader, ConfigData.class);
            if (data == null) {
                this.initDefaults();
                this.save();
                return;
            }
            this.oreRenderDistance = data.oreRenderDistance;
            this.veinMiner = data.veinMiner;
            this.veinMinerFortune = data.veinMinerFortune;
            this.veinMinerSilkTouch = data.veinMinerSilkTouch;
            this.showHitboxes = data.showHitboxes;
            this.showContainerView = data.showContainerView;
            this.shownChangelogVersion = data.shownChangelogVersion != null ? data.shownChangelogVersion : "";
            this.neverShowChangelog = data.neverShowChangelog;
            this.veinMinerDurability = data.veinMinerDurability;
            if (data.installedPlugins != null) {
                this.installedPlugins.addAll(data.installedPlugins);
            }
            this.whitelist.clear();
            for (String id : data.whitelist) {
                resolveBlock(id).ifPresent(this.whitelist::add);
            }
            this.veinMinerWhitelist.clear();
            if (data.veinMinerWhitelist != null) {
                for (String id : data.veinMinerWhitelist) {
                    resolveBlock(id).ifPresent(this.veinMinerWhitelist::add);
                }
            }
            this.containerWhitelist.clear();
            if (data.containerWhitelist != null) {
                for (String id : data.containerWhitelist) {
                    resolveBlock(id).ifPresent(this.containerWhitelist::add);
                }
            }
            this.hitboxEntityWhitelist.clear();
            if (data.hitboxEntityWhitelist != null) {
                this.hitboxEntityWhitelist.addAll(data.hitboxEntityWhitelist);
            }
            if (this.whitelist.isEmpty()) {
                this.initDefaults();
            }
            if (this.veinMinerWhitelist.isEmpty()) {
                this.initVeinMinerDefaults();
            }
            if (this.containerWhitelist.isEmpty()) {
                this.initContainerDefaults();
            }
            if (this.hitboxEntityWhitelist.isEmpty()) {
                this.initHitboxDefaults();
            }
        }
        catch (Exception e) {
            XtoXray.LOGGER.error("Failed to load config", (Throwable)e);
            this.initDefaults();
        }
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("xtoxray.json");
        ConfigData data = new ConfigData();
        data.oreRenderDistance = this.oreRenderDistance;
        data.veinMiner = this.veinMiner;
        data.veinMinerFortune = this.veinMinerFortune;
        data.veinMinerSilkTouch = this.veinMinerSilkTouch;
        data.showHitboxes = this.showHitboxes;
        data.showContainerView = this.showContainerView;
        data.shownChangelogVersion = this.shownChangelogVersion;
        data.neverShowChangelog = this.neverShowChangelog;
        data.veinMinerDurability = this.veinMinerDurability;
        data.installedPlugins.addAll(this.installedPlugins);
        for (Block b : this.whitelist) {
            data.whitelist.add(BuiltInRegistries.BLOCK.getKey(b).toString());
        }
        for (Block b : this.veinMinerWhitelist) {
            data.veinMinerWhitelist.add(BuiltInRegistries.BLOCK.getKey(b).toString());
        }
        for (Block b : this.containerWhitelist) {
            data.containerWhitelist.add(BuiltInRegistries.BLOCK.getKey(b).toString());
        }
        data.hitboxEntityWhitelist.addAll(this.hitboxEntityWhitelist);
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(path, new OpenOption[0]);){
                GSON.toJson((Object)data, (Appendable)writer);
            }
        }
        catch (IOException e) {
            XtoXray.LOGGER.error("Failed to save config", (Throwable)e);
        }
    }

    private void initDefaults() {
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
        this.whitelist.add(Blocks.CHEST);
        this.whitelist.add(Blocks.TRAPPED_CHEST);
        this.whitelist.add(Blocks.SPAWNER);
        this.whitelist.add(Blocks.WATER);
        this.whitelist.add(Blocks.LAVA);
        this.initVeinMinerDefaults();
        this.initContainerDefaults();
        this.initHitboxDefaults();
    }

    private void initVeinMinerDefaults() {
        this.veinMinerWhitelist.add(Blocks.COAL_ORE);
        this.veinMinerWhitelist.add(Blocks.DEEPSLATE_COAL_ORE);
        this.veinMinerWhitelist.add(Blocks.IRON_ORE);
        this.veinMinerWhitelist.add(Blocks.DEEPSLATE_IRON_ORE);
        this.veinMinerWhitelist.add(Blocks.COPPER_ORE);
        this.veinMinerWhitelist.add(Blocks.DEEPSLATE_COPPER_ORE);
        this.veinMinerWhitelist.add(Blocks.GOLD_ORE);
        this.veinMinerWhitelist.add(Blocks.DEEPSLATE_GOLD_ORE);
        this.veinMinerWhitelist.add(Blocks.REDSTONE_ORE);
        this.veinMinerWhitelist.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        this.veinMinerWhitelist.add(Blocks.EMERALD_ORE);
        this.veinMinerWhitelist.add(Blocks.DEEPSLATE_EMERALD_ORE);
        this.veinMinerWhitelist.add(Blocks.LAPIS_ORE);
        this.veinMinerWhitelist.add(Blocks.DEEPSLATE_LAPIS_ORE);
        this.veinMinerWhitelist.add(Blocks.DIAMOND_ORE);
        this.veinMinerWhitelist.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        this.veinMinerWhitelist.add(Blocks.NETHER_GOLD_ORE);
        this.veinMinerWhitelist.add(Blocks.NETHER_QUARTZ_ORE);
        this.veinMinerWhitelist.add(Blocks.ANCIENT_DEBRIS);
    }

    private void initContainerDefaults() {
        this.containerWhitelist.add(Blocks.CHEST);
        this.containerWhitelist.add(Blocks.TRAPPED_CHEST);
        this.containerWhitelist.add(Blocks.BARREL);
        this.containerWhitelist.add(Blocks.SHULKER_BOX);
    }

    private void initHitboxDefaults() {
        this.hitboxEntityWhitelist.add("minecraft:zombie");
        this.hitboxEntityWhitelist.add("minecraft:skeleton");
        this.hitboxEntityWhitelist.add("minecraft:spider");
        this.hitboxEntityWhitelist.add("minecraft:creeper");
        this.hitboxEntityWhitelist.add("minecraft:enderman");
        this.hitboxEntityWhitelist.add("minecraft:witch");
        this.hitboxEntityWhitelist.add("minecraft:slime");
        this.hitboxEntityWhitelist.add("minecraft:ghast");
        this.hitboxEntityWhitelist.add("minecraft:blaze");
        this.hitboxEntityWhitelist.add("minecraft:piglin");
        this.hitboxEntityWhitelist.add("minecraft:warden");
        this.hitboxEntityWhitelist.add("minecraft:husk");
        this.hitboxEntityWhitelist.add("minecraft:stray");
        this.hitboxEntityWhitelist.add("minecraft:drowned");
        this.hitboxEntityWhitelist.add("minecraft:cave_spider");
        this.hitboxEntityWhitelist.add("minecraft:pillager");
        this.hitboxEntityWhitelist.add("minecraft:vindicator");
        this.hitboxEntityWhitelist.add("minecraft:evoker");
        this.hitboxEntityWhitelist.add("minecraft:vex");
        this.hitboxEntityWhitelist.add("minecraft:guardian");
        this.hitboxEntityWhitelist.add("minecraft:elder_guardian");
        this.hitboxEntityWhitelist.add("minecraft:shulker");
        this.hitboxEntityWhitelist.add("minecraft:iron_golem");
        this.hitboxEntityWhitelist.add("minecraft:snow_golem");
        this.hitboxEntityWhitelist.add("minecraft:player");
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isServerAllowsXray() {
        return this.serverAllowsXray;
    }

    public void setServerAllowsXray(boolean v) {
        this.serverAllowsXray = v;
    }

    public void toggle() {
        this.active = !this.active;
    }

    public boolean shouldRender(BlockState state) {
        return !this.active || this.whitelist.contains(state.getBlock()) && !this.disabledBlocks.contains(state.getBlock());
    }

    public Set<Block> getWhitelist() {
        return this.whitelist;
    }

    public boolean isWhitelisted(Block block) {
        return this.whitelist.contains(block);
    }

    public boolean isBlockDisabled(Block block) {
        return this.disabledBlocks.contains(block);
    }

    public void toggleDisabled(Block block) {
        if (this.disabledBlocks.contains(block)) {
            this.disabledBlocks.remove(block);
        } else {
            this.disabledBlocks.add(block);
        }
        this.save();
    }

    public boolean isVeinMiner() {
        return this.veinMiner;
    }

    public void setVeinMiner(boolean veinMiner) {
        this.veinMiner = veinMiner;
        this.save();
    }

    public boolean isVeinMinerFortune() {
        return this.veinMinerFortune;
    }

    public void setVeinMinerFortune(boolean fortune) {
        this.veinMinerFortune = fortune;
        this.save();
    }

    public boolean isVeinMinerSilkTouch() {
        return this.veinMinerSilkTouch;
    }

    public void setVeinMinerSilkTouch(boolean silkTouch) {
        this.veinMinerSilkTouch = silkTouch;
        this.save();
    }

    public int getOreRenderDistance() {
        return this.oreRenderDistance;
    }

    public void setOreRenderDistance(int blocks) {
        this.oreRenderDistance = blocks;
        this.save();
    }

    public boolean isShowHitboxes() {
        return this.showHitboxes;
    }

    public void setShowHitboxes(boolean showHitboxes) {
        this.showHitboxes = showHitboxes;
        this.save();
    }

    public boolean isShowContainerView() {
        return this.showContainerView;
    }

    public void setShowContainerView(boolean showContainerView) {
        this.showContainerView = showContainerView;
        this.save();
    }

    public String getShownChangelogVersion() {
        return this.shownChangelogVersion;
    }

    public void setShownChangelogVersion(String version) {
        this.shownChangelogVersion = version;
        this.save();
    }

    public boolean isNeverShowChangelog() {
        return this.neverShowChangelog;
    }

    public void setNeverShowChangelog(boolean value) {
        this.neverShowChangelog = value;
        this.save();
    }

    public int getVeinMinerDurability() {
        return this.veinMinerDurability;
    }

    public void setVeinMinerDurability(int amount) {
        this.veinMinerDurability = Math.max(0, amount);
        this.save();
    }

    public Set<Block> getVeinMinerWhitelist() {
        return this.veinMinerWhitelist;
    }

    public boolean isVeinMinerWhitelisted(Block block) {
        return this.veinMinerWhitelist.contains(block);
    }

    public Set<Block> getContainerWhitelist() {
        return this.containerWhitelist;
    }

    public boolean isContainerWhitelisted(Block block) {
        return this.containerWhitelist.contains(block);
    }

    public Set<String> getHitboxEntityWhitelist() {
        return this.hitboxEntityWhitelist;
    }

    public boolean isHitboxEntityWhitelisted(String entityId) {
        return this.hitboxEntityWhitelist.contains(entityId);
    }

    public Set<String> getInstalledPlugins() {
        return this.installedPlugins;
    }

    public void setInstalledPlugin(String id, boolean installed) {
        if (installed) {
            this.installedPlugins.add(id);
        } else {
            this.installedPlugins.remove(id);
        }
        this.save();
    }

    /**
     * Resolve a block from its string identifier using reflection.
     * Handles both Identifier.parse() (26.x) and ResourceLocation constructors (1.21.x).
     */
    private static java.util.Optional<Block> resolveBlock(String id) {
        try {
            // Try Identifier.parse() first (MC 26.x)
            Class<?> idClass = Class.forName("net.minecraft.resources.Identifier");
            Object parsed = idClass.getMethod("parse", String.class).invoke(null, id);
            // Use reflection to call BuiltInRegistries.BLOCK.get(Identifier)
            var getMethod = BuiltInRegistries.BLOCK.getClass().getMethod("get", idClass);
            Object holder = getMethod.invoke(BuiltInRegistries.BLOCK, parsed);
            if (holder != null) {
                Object value = holder.getClass().getMethod("value").invoke(holder);
                if (value instanceof Block block) {
                    return java.util.Optional.of(block);
                }
            }
        } catch (Throwable t) {
            try {
                // Fallback: ResourceLocation.tryParse / new ResourceLocation (MC 1.21.x)
                Class<?> rlClass = Class.forName("net.minecraft.resources.ResourceLocation");
                Object parsed;
                try {
                    parsed = rlClass.getMethod("tryParse", String.class).invoke(null, id);
                } catch (Throwable t2) {
                    // 1.21.1 constructor: new ResourceLocation(String namespace, String path)
                    String[] parts = id.split(":", 2);
                    String ns = parts.length > 1 ? parts[0] : "minecraft";
                    String path = parts.length > 1 ? parts[1] : parts[0];
                    parsed = rlClass.getConstructor(String.class, String.class).newInstance(ns, path);
                }
                if (parsed != null) {
                    // Use reflection to call BuiltInRegistries.BLOCK.get(ResourceLocation)
                    var getMethod = BuiltInRegistries.BLOCK.getClass().getMethod("get", rlClass);
                    Object holder = getMethod.invoke(BuiltInRegistries.BLOCK, parsed);
                    if (holder != null) {
                        Object value = holder.getClass().getMethod("value").invoke(holder);
                        if (value instanceof Block block) {
                            return java.util.Optional.of(block);
                        }
                    }
                }
            } catch (Throwable t2) {
                // Give up
            }
        }
        return java.util.Optional.empty();
    }

    private static class ConfigData {
        int oreRenderDistance = 128;
        boolean veinMiner;
        boolean veinMinerFortune = true;
        boolean veinMinerSilkTouch;
        boolean showHitboxes;
        boolean showContainerView = true;
        String shownChangelogVersion = "";
        boolean neverShowChangelog;
        int veinMinerDurability = 1;
        List<String> whitelist = new ArrayList<String>();
        List<String> veinMinerWhitelist = new ArrayList<String>();
        List<String> containerWhitelist = new ArrayList<String>();
        List<String> hitboxEntityWhitelist = new ArrayList<String>();
        List<String> installedPlugins = new ArrayList<String>();

        private ConfigData() {
        }
    }
}

