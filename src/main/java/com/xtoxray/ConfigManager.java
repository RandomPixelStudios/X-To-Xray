package com.xtoxray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.xtoxray.hitboxes.HitboxesState;
import com.xtoxray.plugin.PluginState;
import com.xtoxray.veinminer.VeinminerState;
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

            VeinminerState vs = VeinminerState.getInstance();
            JsonObject vm = new JsonObject();
            vm.addProperty("enabled", vs.isEnabled());
            vm.addProperty("maxBlocks", vs.getMaxBlocks());
            vm.addProperty("silkTouchMode", vs.getSilkTouchMode());
            vm.addProperty("durabilityCost", vs.getDurabilityCost());
            JsonArray vmWhitelist = new JsonArray();
            for (Block b : vs.getWhitelist()) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(b);
                if (id != null && !id.getPath().equals("air")) {
                    vmWhitelist.add(id.toString());
                }
            }
            vm.add("whitelist", vmWhitelist);
            root.add("veinminer", vm);

            HitboxesState hs = HitboxesState.getInstance();
            JsonObject hb = new JsonObject();
            hb.addProperty("enabled", hs.isEnabled());
            hb.addProperty("seeThroughWalls", hs.isSeeThroughWalls());
            hb.addProperty("maxDistance", hs.getMaxDistance());
            JsonArray hbShown = new JsonArray();
            for (int i = 0; i < HitboxesState.CATEGORY_COUNT; i++) {
                hbShown.add(hs.isShown(i));
            }
            hb.add("shown", hbShown);
            JsonArray hbColors = new JsonArray();
            for (int i = 0; i < HitboxesState.CATEGORY_COUNT; i++) {
                hbColors.add(hs.getColor(i));
            }
            hb.add("colors", hbColors);
            root.add("hitboxes", hb);

            JsonObject plugins = new JsonObject();
            for (var p : PluginState.getInstance().getPlugins()) {
                plugins.addProperty(p.getName(), p.isInstalled());
            }
            root.add("plugins", plugins);

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

            if (root.has("veinminer")) {
                JsonObject vm = root.getAsJsonObject("veinminer");
                VeinminerState vs = VeinminerState.getInstance();
                if (vm.has("enabled")) vs.setEnabled(vm.get("enabled").getAsBoolean());
                if (vm.has("maxBlocks")) vs.setMaxBlocks(clamp(vm.get("maxBlocks").getAsInt(), 1, 1000));
                if (vm.has("silkTouchMode")) vs.setSilkTouchMode(vm.get("silkTouchMode").getAsInt());
                if (vm.has("durabilityCost")) vs.setDurabilityCost(clamp(vm.get("durabilityCost").getAsInt(), 0, 100));
                if (vm.has("whitelist")) {
                    vs.getWhitelist().clear();
                    JsonArray arr = vm.getAsJsonArray("whitelist");
                    for (int i = 0; i < arr.size(); i++) {
                        try {
                            Identifier id = Identifier.parse(arr.get(i).getAsString());
                            BuiltInRegistries.BLOCK.get(id).ifPresent(ref -> vs.addToWhitelist(ref.value()));
                        } catch (Exception e) {
                            XtoXray.LOGGER.warn("Skipping invalid veinminer whitelist entry at index {}: {}", i, arr.get(i), e);
                        }
                    }
                }
            }

            if (root.has("hitboxes")) {
                JsonObject hb = root.getAsJsonObject("hitboxes");
                HitboxesState hs = HitboxesState.getInstance();
                if (hb.has("enabled")) hs.setEnabled(hb.get("enabled").getAsBoolean());
                if (hb.has("seeThroughWalls")) hs.setSeeThroughWalls(hb.get("seeThroughWalls").getAsBoolean());
                if (hb.has("maxDistance")) hs.setMaxDistance(hb.get("maxDistance").getAsInt());
                if (hb.has("shown")) {
                    JsonArray arr = hb.getAsJsonArray("shown");
                    for (int i = 0; i < arr.size() && i < HitboxesState.CATEGORY_COUNT; i++) {
                        hs.setShown(i, arr.get(i).getAsBoolean());
                    }
                }
                if (hb.has("colors")) {
                    JsonArray arr = hb.getAsJsonArray("colors");
                    for (int i = 0; i < arr.size() && i < HitboxesState.CATEGORY_COUNT; i++) {
                        hs.setColor(i, arr.get(i).getAsInt());
                    }
                }
            }

            if (root.has("plugins")) {
                JsonObject plugins = root.getAsJsonObject("plugins");
                for (var p : PluginState.getInstance().getPlugins()) {
                    if (plugins.has(p.getName())) {
                        p.setInstalled(plugins.get(p.getName()).getAsBoolean());
                    }
                }
            }
        } catch (Exception e) {
            XtoXray.LOGGER.error("Failed to load config", e);
        }
    }
}
