package com.xtoxray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path getConfigDir() {
        return FabricLoader.getInstance().getGameDir().resolve("config");
    }

    private static Path getConfigFile() {
        return getConfigDir().resolve("xtoxray.json");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();

            XrayState xs = XrayState.getInstance();
            JsonObject xray = new JsonObject();
            xray.addProperty("renderDistance", xs.getRenderDistance());
            xray.addProperty("renderWhileMoving", xs.isRenderWhileMoving());
            xray.addProperty("nightVision", xs.isNightVision());
            JsonArray xrayWhitelist = new JsonArray();
            for (Block b : xs.getWhitelist()) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(b);
                if (id != null && !id.getPath().equals("air")) {
                    xrayWhitelist.add(id.toString());
                }
            }
            xray.add("whitelist", xrayWhitelist);
            root.add("xray", xray);

            Files.createDirectories(getConfigDir());
            Files.writeString(getConfigFile(), GSON.toJson(root));
        } catch (IOException e) {
            XtoXray.LOGGER.error("Failed to save config", e);
        }
    }

    public static void load() {
        try {
            if (!Files.exists(getConfigFile())) return;
            String json = Files.readString(getConfigFile());
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            if (root.has("xray")) {
                JsonObject xray = root.getAsJsonObject("xray");
                XrayState xs = XrayState.getInstance();
                if (xray.has("renderDistance")) xs.setRenderDistance(clamp(xray.get("renderDistance").getAsInt(), 1, 64));
                if (xray.has("renderWhileMoving")) xs.setRenderWhileMoving(xray.get("renderWhileMoving").getAsBoolean());
                if (xray.has("nightVision")) xs.setNightVision(xray.get("nightVision").getAsBoolean());
                if (xray.has("whitelist")) {
                    xs.getWhitelist().clear();
                    JsonArray arr = xray.getAsJsonArray("whitelist");
                    for (int i = 0; i < arr.size(); i++) {
                        try {
                            Identifier id = Identifier.parse(arr.get(i).getAsString());
                            BuiltInRegistries.BLOCK.get(id).ifPresent(ref -> xs.addToWhitelist(ref.value()));
                        } catch (Exception e) {
                            XtoXray.LOGGER.warn("Skipping invalid whitelist entry at index {}: {}", i, arr.get(i), e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            XtoXray.LOGGER.error("Failed to load config", e);
        }
    }
}
