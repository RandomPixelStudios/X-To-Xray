package com.xtoxray.client.news;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xtoxray.XtoXray;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class NewsManager {
    public static final long DISPLAY_MS = 10000;
    public static final long FADE_MS = 3000;

    public record Frame(NewsEntry oldEntry, NewsEntry current, float progress, float fade) {
    }

    public record Layout(int w, int h, boolean compact) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static List<NewsEntry> entries = new ArrayList<>();
    private static boolean initialized = false;
    private static long knownModified = -1;
    private static long lastCheck = 0;

    private NewsManager() {
    }

    public static synchronized void init() {
        load(true);
        initialized = true;
    }

    public static Layout layoutFor(int screenWidth, int screenHeight) {
        if (screenWidth < 430 || screenHeight < 260) {
            return null;
        }
        if (screenWidth < 600) {
            return new Layout(150, 54, true);
        }
        return new Layout(184, 70, false);
    }

    public static synchronized Frame frame() {
        ensureLoaded();
        List<NewsEntry> imgs = new ArrayList<>();
        for (NewsEntry entry : entries) {
            if (entry.hasImage()) {
                imgs.add(entry);
            }
        }
        if (imgs.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        long cycle = DISPLAY_MS + FADE_MS;
        long slot = now / cycle;
        int n = imgs.size();
        int index = (int) (slot % n);
        long phase = now % cycle;
        if (phase < DISPLAY_MS) {
            return new Frame(null, imgs.get(index), (float) phase / DISPLAY_MS, -1.0f);
        }
        int next = (int) ((slot + 1) % n);
        float t = (float) (phase - DISPLAY_MS) / FADE_MS;
        return new Frame(imgs.get(index), imgs.get(next), 1.0f, Math.min(1.0f, t));
    }

    public static synchronized NewsEntry getCurrentEntry() {
        Frame frame = frame();
        return frame == null ? null : frame.current();
    }

    private static void ensureLoaded() {
        if (!initialized) {
            load(false);
            initialized = true;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) {
            return;
        }
        lastCheck = now;
        try {
            Path file = getFile();
            if (Files.exists(file)) {
                long modified = Files.getLastModifiedTime(file).toMillis();
                if (modified != knownModified) {
                    load(false);
                }
            }
        } catch (Exception e) {
            XtoXray.LOGGER.warn("Konnte News-Datei nicht pruefen, nutze Cache", e);
        }
    }

    private static void load(boolean writeDefaultsIfMissing) {
        try {
            Path file = getFile();
            if (!Files.exists(file)) {
                entries = defaults();
                if (writeDefaultsIfMissing) {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, GSON.toJson(toJson(entries)));
                }
                knownModified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
                return;
            }
            knownModified = Files.getLastModifiedTime(file).toMillis();
            List<NewsEntry> parsed = parse(Files.readString(file));
            entries = parsed.isEmpty() ? defaults() : parsed;
            applyDefaultImages(entries);
        } catch (Exception e) {
            XtoXray.LOGGER.warn("Konnte News nicht laden, nutze Defaults", e);
            entries = defaults();
        }
    }

    private static List<NewsEntry> parse(String json) {
        List<NewsEntry> out = new ArrayList<>();
        JsonElement root = GSON.fromJson(json, JsonElement.class);
        if (root == null || !root.isJsonArray()) {
            return out;
        }
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("title")) {
                    continue;
                }
                String title = obj.get("title").getAsString();
                List<String> lines = new ArrayList<>();
                if (obj.has("lines") && obj.get("lines").isJsonArray()) {
                    for (JsonElement line : obj.getAsJsonArray("lines")) {
                        lines.add(line.getAsString());
                    }
                }
                int color = obj.has("color") ? obj.get("color").getAsInt() : 0x55FFFF;
                String url = obj.has("url") ? obj.get("url").getAsString() : "";
                String image = obj.has("image") ? obj.get("image").getAsString() : "";
                out.add(new NewsEntry(title, lines, color, url, image));
            } catch (Exception e) {
                XtoXray.LOGGER.warn("Ignoriere ungueltigen News-Eintrag", e);
            }
        }
        return out;
    }

    private static JsonArray toJson(List<NewsEntry> list) {
        JsonArray arr = new JsonArray();
        for (NewsEntry entry : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("title", entry.getTitle());
            JsonArray lines = new JsonArray();
            for (String line : entry.getLines()) {
                lines.add(line);
            }
            obj.add("lines", lines);
            obj.addProperty("color", entry.getColor());
            obj.addProperty("url", entry.getUrl());
            obj.addProperty("image", entry.getImage());
            arr.add(obj);
        }
        return arr;
    }

    private static void applyDefaultImages(List<NewsEntry> list) {
        List<NewsEntry> base = defaults();
        for (NewsEntry entry : list) {
            if (entry.hasImage()) {
                continue;
            }
            for (NewsEntry def : base) {
                if (def.hasImage() && def.getTitle().equals(entry.getTitle())
                        && def.getUrl().equals(entry.getUrl())) {
                    entry.setImage(def.getImage());
                    break;
                }
            }
        }
    }

    private static List<NewsEntry> defaults() {
        List<NewsEntry> list = new ArrayList<>();
        list.add(new NewsEntry("X To Xray Update",
                List.of("Version 2026.28 ist da!", "Druecke X im Spiel."),
                0x55FF55, "https://modrinth.com/mod/x-to-xray",
                "xtoxray:textures/gui/news/1.png"));
        list.add(new NewsEntry("YouTube",
                List.of("Subscribe!", "Neue Videos & Tutorials."),
                0xFF5555, "https://www.youtube.com/@RandomPixelStudios",
                "xtoxray:textures/gui/news/2.png"));
        return list;
    }

    private static Path getFile() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("xtoxray-news.json");
    }
}
