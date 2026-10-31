package com.xtoxray.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xtoxray.ConfigManager;
import com.xtoxray.XrayState;
import com.xtoxray.client.news.NewsManager;
import com.xtoxray.network.HandshakeNetworking;
import com.xtoxray.network.HandshakeState;
import com.xtoxray.plugin.PluginManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.net.URI;

public class XrayToggleHandler implements ClientModInitializer {
    private static final InputConstants.Key KEY_TOGGLE = InputConstants.getKey("key.keyboard.x");
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.xtoxray.toggle", KEY_TOGGLE.getType(), KEY_TOGGLE.getValue(), KeyMapping.Category.MISC);
    private static SectionPos lastRebuildPos = SectionPos.of(0, 0, 0);

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(TOGGLE_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(XrayToggleHandler::onTick);
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_GIZMOS.register(context -> PluginManager.getInstance().fireRender());
        PluginManager.getInstance().init();
        NewsManager.init();
        HandshakeNetworking.registerClient();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Component youtube = Component.literal("[YouTube]").withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.youtube.com/@RandomPixelStudios"))).withColor(0xFF0000));
            Component msg = Component.literal("Support X To Xray By Subscribing On Youtube! ").append(youtube);
            if (client.player != null) {
                client.player.sendSystemMessage(msg);
            }
        });
    }

    private static void onTick(Minecraft client) {
        PluginManager.getInstance().fire("tick");
        HandshakeState hs = HandshakeState.getInstance();
        if (client.player != null && client.level != null && client.getCurrentServer() != null) {
            hs.tick();
        }
        if (client.player != null && client.level != null && client.getCurrentServer() != null
            && !hs.isServerHasMod() && XrayState.getInstance().isActive()) {
            // Xray may have been switched on in singleplayer; it must not stay on when joining a server.
            XrayState.getInstance().toggle();
            client.player.removeEffect(MobEffects.NIGHT_VISION);
            rebuildAllSections(client);
            ConfigManager.save();
            PluginManager.getInstance().fire("xray_toggle", org.luaj.vm2.LuaValue.valueOf(false));
        }
        while (TOGGLE_KEY.consumeClick()) {
            if (client.level == null || client.player == null) continue;
            if (client.getCurrentServer() != null && !hs.isServerHasMod()) {
                client.player.sendSystemMessage(Component.literal("The X To Xray Serverside Is Not Installed!").withStyle(net.minecraft.ChatFormatting.RED));
                continue;
            }
            XrayState state = XrayState.getInstance();
            state.toggle();
            if (state.isActive()) {
                if (state.isNightVision()) {
                    client.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
                }
                lastRebuildPos = SectionPos.of(client.player.blockPosition());
            } else {
                client.player.removeEffect(MobEffects.NIGHT_VISION);
            }
            rebuildAllSections(client);
            ConfigManager.save();
            PluginManager.getInstance().fire("xray_toggle", org.luaj.vm2.LuaValue.valueOf(state.isActive()));
        }
        if (client.player != null && client.level != null) {
            XrayState state = XrayState.getInstance();
            if (state.isActive()) {
                if (!state.isRenderWhileMoving()) {
                    boolean isMoving = client.player.getDeltaMovement().lengthSqr() > 0.01;
                    state.setRenderingEnabled(!isMoving);
                    if (state.isNightVision()) {
                        if (isMoving) {
                            client.player.removeEffect(MobEffects.NIGHT_VISION);
                        } else {
                            client.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
                        }
                    }
                } else {
                    state.setRenderingEnabled(true);
                    SectionPos currentPos = SectionPos.of(client.player.blockPosition());
                    if (!currentPos.equals(lastRebuildPos)) {
                        lastRebuildPos = currentPos;
                        rebuildAllSections(client);
                    }
                }
            }
        }
    }

    public static void rebuildAllSections(Minecraft client) {
        if (client.levelRenderer == null || client.level == null || client.player == null) return;
        try {
            SectionPos cameraPos = SectionPos.of(client.player.blockPosition());
            int radius = client.options.getEffectiveRenderDistance();
            int minY = client.level.getMinSectionY();
            int maxY = client.level.getMaxSectionY() - 1;
            int minX = cameraPos.getX() - radius;
            int maxX = cameraPos.getX() + radius;
            int minZ = cameraPos.getZ() - radius;
            int maxZ = cameraPos.getZ() + radius;
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sx = minX; sx <= maxX; sx++) {
                    for (int sz = minZ; sz <= maxZ; sz++) {
                        client.level.setSectionDirtyWithNeighbors(sx, sy, sz);
                    }
                }
            }
        } catch (Throwable e) {
            try {
                client.levelRenderer.getClass().getMethod("allChanged").invoke(client.levelRenderer);
            } catch (Throwable ignored) {}
            for (int sy = client.level.getMinSectionY(); sy <= client.level.getMaxSectionY() - 1; sy++) {
                for (int sx = -64; sx <= 64; sx++) {
                    for (int sz = -64; sz <= 64; sz++) {
                        try {
                            client.level.getClass().getMethod("setSectionDirty", int.class, int.class, int.class)
                                .invoke(client.level, sx, sy, sz);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }
}