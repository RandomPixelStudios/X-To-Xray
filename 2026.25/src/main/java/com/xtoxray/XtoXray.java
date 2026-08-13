/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.IntegerArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  net.fabricmc.api.ModInitializer
 *  net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
 *  net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.server.level.ServerPlayer
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.xtoxray;

import com.xtoxray.network.XrayPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod entrypoint — ZERO MC imports.
 * All MC-dependent code is delegated to helper classes loaded via Class.forName().
 */
public class XtoXray implements ModInitializer {
    public static final String MOD_ID = "xtoxray";
    public static final Logger LOGGER = LoggerFactory.getLogger("xtoxray");
    public static final String VERSION = FabricLoader.getInstance()
        .getModContainer("xtoxray")
        .map(c -> c.getMetadata().getVersion().getFriendlyString())
        .orElse("unknown");

    @Override
    public void onInitialize() {
        LOGGER.info("XtoXray {} initialized on Minecraft!", VERSION);

        // Load config — may fail if MC classes are missing
        try {
            XrayState.getInstance().load();
        } catch (Throwable t) {
            LOGGER.warn("XtoXray: Failed to load config (non-fatal)", t);
        }

        // Register vein miner — may fail if MC classes are missing
        try {
            XrayVeinMiner.register();
        } catch (Throwable t) {
            LOGGER.warn("XtoXray: Failed to register vein miner (non-fatal)", t);
        }

        // Register network payloads — loaded via reflection for cross-version compat
        try {
            XrayPayloads.registerServer();
            LOGGER.info("XtoXray: Network payloads registered successfully");
        } catch (Throwable t) {
            LOGGER.warn("XtoXray: Networking unavailable on this MC version (server sync disabled)", t);
        }

        // Register commands — loaded via reflection (isolated MC imports)
        try {
            Class.forName("com.xtoxray.CommandRegistrar")
                .getMethod("register")
                .invoke(null);
            LOGGER.info("XtoXray: Commands registered");
        } catch (Throwable t) {
            LOGGER.warn("XtoXray: Commands unavailable on this MC version", t);
        }
    }
}

