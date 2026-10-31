package com.xtoxray.plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/** Downloads over HTTPS from GitHub only. Trust comes from the signature and hashes, not from the connection. */
final class PluginRepository {
    static final String BASE_URL = "https://raw.githubusercontent.com/RandomPixelStudios/X-To-Xray-Plugins/index/";
    static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAX1aKoirKhDNauFVA1DEoAt+eyq6cu9Z16Nst76svUCU=";
    private static final Set<String> ALLOWED_HOSTS = Set.of("raw.githubusercontent.com", "api.github.com", "github.com",
        "objects.githubusercontent.com", "release-assets.githubusercontent.com");
    private static final int MAX_INDEX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private final Path cacheFolder;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    PluginRepository(Path cacheFolder) {
        this.cacheFolder = cacheFolder;
    }

    /** Last verified index from disk, so revocations still apply when offline. */
    RepositoryIndex loadCached() {
        try {
            Path json = cacheFolder.resolve("index.json");
            if (!Files.exists(json)) return null;
            return RepositoryIndex.parseSigned(Files.readAllBytes(json), Files.readAllBytes(cacheFolder.resolve("index.json.sig")), PUBLIC_KEY, 0);
        } catch (IOException | PluginLoadException e) {
            return null;
        }
    }

    RepositoryIndex fetch(long minSequence) throws IOException, InterruptedException, PluginLoadException {
        byte[] json = download(BASE_URL + "index.json", MAX_INDEX_BYTES);
        byte[] signature = download(BASE_URL + "index.json.sig", 4096);
        RepositoryIndex index = RepositoryIndex.parseSigned(json, signature, PUBLIC_KEY, minSequence);
        Files.createDirectories(cacheFolder);
        Files.write(cacheFolder.resolve("index.json"), json);
        Files.write(cacheFolder.resolve("index.json.sig"), signature);
        return index;
    }

    /** Display only: read from GitHub's release statistics, not signed. Key is the plugin id. */
    java.util.Map<String, Long> fetchDownloads() throws IOException, InterruptedException, PluginLoadException {
        byte[] json = download("https://api.github.com/repos/RandomPixelStudios/X-To-Xray-Plugins/releases/tags/plugins", MAX_INDEX_BYTES);
        Files.createDirectories(cacheFolder);
        Files.write(cacheFolder.resolve("downloads.json"), json);
        return parseDownloads(new String(json, java.nio.charset.StandardCharsets.UTF_8));
    }

    java.util.Map<String, Long> loadCachedDownloads() {
        try {
            Path file = cacheFolder.resolve("downloads.json");
            return Files.exists(file) ? parseDownloads(Files.readString(file)) : java.util.Map.of();
        } catch (IOException | RuntimeException e) {
            return java.util.Map.of();
        }
    }

    static java.util.Map<String, Long> parseDownloads(String json) {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (var element : com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("assets")) {
            var asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            int dash = name.lastIndexOf('-');
            if (dash > 0) counts.merge(name.substring(0, dash), asset.get("download_count").getAsLong(), Long::sum);
        }
        return counts;
    }

    /** Fetches the release asset so GitHub counts the download. The installed code always comes from the signed list. */
    void countDownload(PluginDefinition definition) {
        if (Boolean.getBoolean("xtoxray.noDownloadCount") || !definition.version().matches("[0-9A-Za-z._]{1,32}")) return;
        try {
            download("https://github.com/RandomPixelStudios/X-To-Xray-Plugins/releases/download/plugins/"
                + definition.id() + "-" + definition.version() + ".lua", PluginDefinition.MAX_BYTES);
        } catch (IOException | PluginLoadException e) {
            // The counter is optional.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private byte[] download(String url, int limit) throws IOException, InterruptedException, PluginLoadException {
        URI uri = URI.create(url);
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            if (!"https".equals(uri.getScheme()) || !ALLOWED_HOSTS.contains(uri.getHost())) {
                throw new PluginLoadException("Download host not allowed: " + uri.getHost());
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "XtoXray-Plugins").GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                int code = response.statusCode();
                if (code >= 300 && code < 400) {
                    uri = uri.resolve(response.headers().firstValue("location")
                        .orElseThrow(() -> new IOException("Redirect without location")));
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code + " for " + uri.getHost());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = body.read(buffer)) != -1) {
                    if (out.size() + read > limit) throw new PluginLoadException("Download is too large");
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        }
        throw new IOException("Too many redirects");
    }
}
