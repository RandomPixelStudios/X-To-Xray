/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 */
package com.xtoxray.client;

import com.xtoxray.XrayState;
import com.xtoxray.client.ContainerViewHandler;
import com.xtoxray.client.VersionAdapter;
import com.xtoxray.client.XrayToggleHandler;
import com.xtoxray.network.XrayPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.LoggerFactory;

public class XtoXrayClient
implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        org.slf4j.Logger logger = LoggerFactory.getLogger("xtoxray");
        logger.info("XtoXray client initializing on MC {} (detected: {})", VersionAdapter.getVersionString(), VersionAdapter.getVersion());

        // Load config — may fail if MC classes are missing
        try {
            XrayState.getInstance().load();
        } catch (Throwable t) {
            logger.warn("XtoXray: Failed to load config (non-fatal)", t);
        }

        // Debug Log plugin support
        try {
            DebugLogForwarder.init();
            DebugLogForwarder.registerHud();
            if (XrayState.getInstance().getInstalledPlugins().contains("debug_plugin")) {
                DebugLogForwarder.install();
            }
        } catch (Throwable t) {
            logger.warn("XtoXray: DebugLogForwarder init failed", t);
        }

        // Register handlers — may fail if MC classes are missing
        try { XrayToggleHandler.register(); } catch (Throwable t) {
            logger.warn("XtoXray: XrayToggleHandler unavailable", t);
        }
        try { ContainerViewHandler.register(); } catch (Throwable t) {
            logger.warn("XtoXray: ContainerViewHandler unavailable", t);
        }
        // Network registration — loaded via reflection for cross-version compat
        try {
            XrayPayloads.registerClient();
        } catch (Throwable t) {
            logger.warn("XtoXray: Client network registration failed (server sync disabled)", t);
        }

        // Disconnect handler — Fabric API, should be available on all versions
        try {
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                XrayState.getInstance().setActive(false);
                XrayState.getInstance().setServerAllowsXray(false);
            });
        } catch (Throwable t) {
            logger.warn("XtoXray: Disconnect handler unavailable", t);
        }
    }
}

