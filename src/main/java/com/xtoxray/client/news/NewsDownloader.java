package com.xtoxray.client.news;

import com.mojang.blaze3d.platform.NativeImage;
import com.xtoxray.XtoXray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Loads the main menu news (text and images) from GitHub, like the plugin list.
 * The last good download is cached so the news also show up offline.
 */
final class NewsDownloader {
    static final String BASE_URL = System.getProperty("xtoxray.newsBaseUrl",
        "https://raw.githubusercontent.com/RandomPixelStudios/X-To-Xray/main/news/");
    private static final String HOST = "raw.githubusercontent.com";
    private static final int MAX_JSON_BYTES = 64 * 1024;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ENTRIES = 16;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private static int generation = 0;

    private NewsDownloader() {
    }

    /** Shows the cached news right away, then refreshes them from GitHub in the background. */
    private static boolean started;

    static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        if (Boolean.getBoolean("xtoxray.noRemoteNews")) {
            return;
        }
        Thread thread = new Thread(NewsDownloader::refresh, "XtoXray-News");
        thread.setDaemon(true);
        thread.start();
    }

    private static void refresh() {
        try {
            List<NewsEntry> cached = loadCache();
            if (!cached.isEmpty()) {
                runOnClient(() -> NewsManager.setRemote(cached));
            }
        } catch (Exception e) {
            XtoXray.LOGGER.warn("Konnte News-Cache nicht laden", e);
        }
        try {
            byte[] json = download(BASE_URL + "news.json", MAX_JSON_BYTES);
            List<NewsEntry> parsed = NewsManager.parse(new String(json, StandardCharsets.UTF_8));
            List<NewsEntry> result = new ArrayList<>();
            List<byte[]> images = new ArrayList<>();
            for (NewsEntry entry : parsed) {
                if (result.size() >= MAX_ENTRIES || !validName(entry.getImage())) {
                    continue;
                }
                try {
                    byte[] png = download(BASE_URL + entry.getImage(), MAX_IMAGE_BYTES);
                    if (!isPng(png)) {
                        throw new IOException("Kein PNG");
                    }
                    result.add(entry);
                    images.add(png);
                } catch (Exception e) {
                    XtoXray.LOGGER.warn("Konnte News-Bild {} nicht laden", entry.getImage(), e);
                }
            }
            if (result.isEmpty()) {
                return;
            }
            writeCache(json, result, images);
            List<NewsEntry> entries = loadCache();
            runOnClient(() -> NewsManager.setRemote(entries));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            XtoXray.LOGGER.info("News von GitHub nicht erreichbar, nutze Cache/Defaults: {}", e.toString());
        }
    }

    /** Reads the cached news and turns their images into textures. Must run where textures may be created. */
    private static List<NewsEntry> loadCache() throws IOException {
        Path dir = cacheDir();
        Path jsonFile = dir.resolve("news.json");
        if (!Files.exists(jsonFile)) {
            return List.of();
        }
        List<NewsEntry> entries = new ArrayList<>();
        for (NewsEntry entry : NewsManager.parse(Files.readString(jsonFile))) {
            if (validName(entry.getImage()) && Files.exists(dir.resolve(entry.getImage()))) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** Called on the render thread: uploads the PNGs as textures and points the entries at them. */
    static List<NewsEntry> registerTextures(List<NewsEntry> entries) {
        int gen = ++generation;
        List<NewsEntry> out = new ArrayList<>();
        Path dir = cacheDir();
        for (int i = 0; i < entries.size(); i++) {
            NewsEntry entry = entries.get(i);
            try (InputStream in = Files.newInputStream(dir.resolve(entry.getImage()))) {
                NativeImage image = NativeImage.read(in);
                Identifier id = Identifier.fromNamespaceAndPath(XtoXray.MOD_ID, "news_remote/" + gen + "_" + i);
                float aspect = (float) image.getWidth() / (float) image.getHeight();
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
                NewsImage.put(id.toString(), aspect);
                out.add(new NewsEntry(entry.getTitle(), entry.getLines(), entry.getColor(), entry.getUrl(), id.toString()));
            } catch (Exception e) {
                XtoXray.LOGGER.warn("Konnte News-Bild {} nicht anzeigen", entry.getImage(), e);
            }
        }
        return out;
    }

    private static void writeCache(byte[] json, List<NewsEntry> entries, List<byte[]> images) throws IOException {
        Path dir = cacheDir();
        Files.createDirectories(dir);
        for (int i = 0; i < entries.size(); i++) {
            Files.write(dir.resolve(entries.get(i).getImage()), images.get(i));
        }
        Files.write(dir.resolve("news.json"), json);
    }

    /** The client may not exist yet when the mod starts, so wait for it. */
    private static void runOnClient(Runnable task) throws InterruptedException {
        while (Minecraft.getInstance() == null) {
            Thread.sleep(100);
        }
        Minecraft.getInstance().execute(task);
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,64}\\.png");
    }

    private static boolean isPng(byte[] data) {
        if (data.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static Path cacheDir() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("xtoxray-news-cache");
    }

    private static byte[] download(String url, int limit) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) {
            throw new IOException("Host nicht erlaubt: " + uri.getHost());
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("User-Agent", "XtoXray-News").GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " fuer " + uri.getPath());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = body.read(buffer)) != -1) {
                if (out.size() + read > limit) {
                    throw new IOException("Download zu gross");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
