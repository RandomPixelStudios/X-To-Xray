package com.xtoxray.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xtoxray.XrayState;
import com.xtoxray.XtoXray;
import com.xtoxray.client.VersionAdapter;
import com.xtoxray.client.gui.XrayModMenu;
import com.xtoxray.network.XrayPayloads;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class XrayToggleHandler {
    private static final InputConstants.Key KEY_TOGGLE = InputConstants.getKey("key.keyboard.x");
    private static final InputConstants.Key KEY_SETTINGS = InputConstants.getKey("key.keyboard.right.shift");
    private static final InputConstants.Key KEY_UNKNOWN = InputConstants.UNKNOWN;

    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.xtoxray.toggle", KEY_TOGGLE.getType(), KEY_TOGGLE.getValue(), KeyMapping.Category.MISC);
    public static final KeyMapping HITBOXES_KEY = new KeyMapping("key.xtoxray.hitboxes", KEY_UNKNOWN.getType(), KEY_UNKNOWN.getValue(), KeyMapping.Category.MISC);
    public static final KeyMapping CONTAINER_VIEW_KEY = new KeyMapping("key.xtoxray.containerview", KEY_UNKNOWN.getType(), KEY_UNKNOWN.getValue(), KeyMapping.Category.MISC);
    public static final KeyMapping VEIN_MINER_KEY = new KeyMapping("key.xtoxray.veinminer", KEY_UNKNOWN.getType(), KEY_UNKNOWN.getValue(), KeyMapping.Category.MISC);
    public static final KeyMapping SETTINGS_KEY = new KeyMapping("key.xtoxray.settings", KEY_SETTINGS.getType(), KEY_SETTINGS.getValue(), KeyMapping.Category.MISC);
    private static final KeyMapping[] ALL_KEYS = new KeyMapping[]{TOGGLE_KEY, HITBOXES_KEY, CONTAINER_VIEW_KEY, VEIN_MINER_KEY, SETTINGS_KEY};
    private static SectionPos lastSectionPos;
    private static final boolean sodiumLoaded;
    private static final Method sodiumSchedule;

    public static KeyMapping getToggleKey() {
        return TOGGLE_KEY;
    }

    public static KeyMapping[] getAllKeyMappings() {
        return ALL_KEYS;
    }

    public static void register() {
        for (KeyMapping km : ALL_KEYS) {
            KeyMappingHelper.registerKeyMapping(km);
        }
        ClientTickEvents.END_CLIENT_TICK.register(XrayToggleHandler::onTick);
    }

    private static void onTick(Minecraft client) {
        SectionPos current;
        XrayState state = XrayState.getInstance();
        while (HITBOXES_KEY.consumeClick()) {
            state.setShowHitboxes(!state.isShowHitboxes());
        }
        while (CONTAINER_VIEW_KEY.consumeClick()) {
            state.setShowContainerView(!state.isShowContainerView());
        }
        while (VEIN_MINER_KEY.consumeClick()) {
            state.setVeinMiner(!state.isVeinMiner());
            if (client.getConnection() == null) continue;
            if (XrayPayloads.isAvailable()) {
                try {
                    XrayPayloads.sendSyncVeinMiner(state.isVeinMiner());
                } catch (Throwable ignored) {}
            }
        }
        while (SETTINGS_KEY.consumeClick()) {
            try {
                VersionAdapter.setScreen(client, new XrayModMenu(VersionAdapter.getScreen(client)));
            } catch (Throwable t) {
                // GUI screens may not load on older MC versions (missing GuiGraphicsExtractor etc.)
                XtoXray.LOGGER.warn("XtoXray: Settings screen unavailable on MC {}: {}", VersionAdapter.getVersionString(), t.getMessage());
                client.player.sendSystemMessage(Component.literal("\u00a7cXtoXray: Settings screen not available on this MC version"));
            }
        }
        while (TOGGLE_KEY.consumeClick()) {
            if (client.level == null || client.player == null) continue;
            if (client.getCurrentServer() != null && !isRealmsServer(client) && !state.isServerAllowsXray()) {
                client.player.sendSystemMessage((Component)Component.literal((String)"\u00a7cXray is blocked on this server (install XtoXray on the server too)"));
                DebugLogForwarder.incrementXrayError();
                continue;
            }
            state.toggle();
            DebugLogForwarder.incrementXrayUse();
            DebugLogForwarder.sendStatsToChat();
            if (state.isActive()) {
                client.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
                lastSectionPos = SectionPos.of((BlockPos)client.player.blockPosition());
            } else {
                client.player.removeEffect(MobEffects.NIGHT_VISION);
                lastSectionPos = null;
            }
            XrayToggleHandler.rebuildAllSections(client);
        }
        if (state.isActive() && client.level != null && client.player != null && !(current = SectionPos.of((BlockPos)client.player.blockPosition())).equals((Object)lastSectionPos)) {
            lastSectionPos = current;
        }
    }

    public static void rebuildAllSections(Minecraft client) {
        if (client.levelRenderer == null || client.level == null || client.player == null) {
            return;
        }
        // ─── Sodium fast path ──────────────────────────────────────────
        if (sodiumLoaded && sodiumSchedule != null) {
            ClientLevel heightAccessor = client.level;
            int minSY = heightAccessor.getMinSectionY();
            int maxSY = heightAccessor.getMaxSectionY() - 1;
            SectionPos cameraPos = SectionPos.of((BlockPos)client.player.blockPosition());
            int cx = cameraPos.getX();
            int cz = cameraPos.getZ();
            int radius = client.options.getEffectiveRenderDistance();
            int minBX = SectionPos.sectionToBlockCoord((int)(cx - radius));
            int minBY = SectionPos.sectionToBlockCoord((int)minSY);
            int minBZ = SectionPos.sectionToBlockCoord((int)(cz - radius));
            int maxBX = SectionPos.sectionToBlockCoord((int)(cx + radius + 1)) - 1;
            int maxBY = SectionPos.sectionToBlockCoord((int)(maxSY + 1)) - 1;
            int maxBZ = SectionPos.sectionToBlockCoord((int)(cz + radius + 1)) - 1;
            try {
                Object swr = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
                    .getMethod("instanceNullable", new Class[0]).invoke(null, new Object[0]);
                if (swr != null) {
                    sodiumSchedule.invoke(swr, minBX, minBY, minBZ, maxBX, maxBY, maxBZ, true);
                    XtoXray.LOGGER.info("XtoXray: Sodium section rebuild triggered via scheduleRebuildForBlockArea");
                    return;
                } else {
                    XtoXray.LOGGER.warn("XtoXray: SodiumWorldRenderer.instanceNullable() returned null — trying fallback");
                }
            } catch (Exception e) {
                XtoXray.LOGGER.warn("XtoXray: Sodium rebuild failed: {}", e.getMessage());
            }
            // Fallback A: try scheduleRebuildForChunks (chunk coords, more direct)
            try {
                Object swr = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
                    .getMethod("instanceNullable", new Class[0]).invoke(null, new Object[0]);
                if (swr != null) {
                    Method schedChunks = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
                        .getMethod("scheduleRebuildForChunks", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Boolean.TYPE);
                    schedChunks.invoke(swr, cx - radius, minSY, cz - radius, cx + radius, maxSY, cz + radius, true);
                    XtoXray.LOGGER.info("XtoXray: Sodium chunk rebuild triggered via scheduleRebuildForChunks");
                    return;
                }
            } catch (Exception e) {
                XtoXray.LOGGER.debug("XtoXray: Sodium scheduleRebuildForChunks fallback also failed: {}", e.getMessage());
            }
            // Fallback B: try scheduleTerrainUpdate (marks graph dirty, forces re-evaluation)
            try {
                Object swr = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
                    .getMethod("instanceNullable", new Class[0]).invoke(null, new Object[0]);
                if (swr != null) {
                    Method schedTerrain = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
                        .getMethod("scheduleTerrainUpdate");
                    schedTerrain.invoke(swr);
                    XtoXray.LOGGER.info("XtoXray: Sodium terrain update triggered via scheduleTerrainUpdate");
                    return;
                }
            } catch (Exception e) {
                XtoXray.LOGGER.debug("XtoXray: Sodium scheduleTerrainUpdate fallback also failed: {}", e.getMessage());
            }
        } else {
            XtoXray.LOGGER.debug("XtoXray: Sodium not loaded (sodiumLoaded={}), trying vanilla path", sodiumLoaded);
        }
        // ─── Vanilla path (also works as fallback when Sodium block-area rebuild fails) ──
        // Mark every section in the render distance as dirty so the vanilla pipeline
        // recompiles them through our RenderSectionRegion mixin (same path as a block update).
        try {
            SectionPos cameraPos = SectionPos.of((BlockPos) client.player.blockPosition());
            int radius = client.options.getEffectiveRenderDistance();
            int minY = client.level.getMinSectionY();
            int maxY = client.level.getMaxSectionY() - 1;
            int minX = cameraPos.getX() - radius;
            int maxX = cameraPos.getX() + radius;
            int minZ = cameraPos.getZ() - radius;
            int maxZ = cameraPos.getZ() + radius;
            int count = 0;
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sx = minX; sx <= maxX; sx++) {
                    for (int sz = minZ; sz <= maxZ; sz++) {
                        client.level.setSectionDirtyWithNeighbors(sx, sy, sz);
                        count++;
                    }
                }
            }
            XtoXray.LOGGER.info("XtoXray: Marked {} sections dirty via setSectionDirtyWithNeighbors", count);
        } catch (Throwable e) {
            // Nuclear fallback: try allChanged() which forces a full re-render
            try {
                java.lang.reflect.Method allChanged = client.levelRenderer.getClass().getMethod("allChanged");
                allChanged.invoke(client.levelRenderer);
                XtoXray.LOGGER.info("XtoXray: Used allChanged() nuclear fallback for section rebuild");
            } catch (Throwable ignored) {
                XtoXray.LOGGER.warn("XtoXray: Failed to mark sections dirty", e);
            }
        }
    }

    private static boolean isRealmsServer(Minecraft client) {
        ServerData server = client.getCurrentServer();
        if (server == null) return false;
        try {
            return server.isRealm();
        } catch (Exception e) {
            return false;
        }
    }

    static {
        boolean loaded = false;
        Method m = null;
        String failReason = "unknown";
        // Try the primary Sodium package path (current/1.21.x+)
        try {
            Class<?> cls = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            m = cls.getMethod("scheduleRebuildForBlockArea", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Boolean.TYPE);
            loaded = true;
        } catch (ClassNotFoundException e) {
            failReason = "SodiumWorldRenderer class not found (Sodium not installed?)";
        } catch (NoSuchMethodException e) {
            failReason = "scheduleRebuildForBlockArea method not found — Sodium API changed?";
        } catch (Exception e) {
            failReason = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        sodiumLoaded = loaded;
        sodiumSchedule = m;
        if (loaded) {
            XtoXray.LOGGER.info("XtoXray: Sodium detected — section rebuild will use SodiumWorldRenderer");
        } else {
            XtoXray.LOGGER.info("XtoXray: Sodium not detected ({}) — section rebuild will use vanilla path", failReason);
        }
    }
}
