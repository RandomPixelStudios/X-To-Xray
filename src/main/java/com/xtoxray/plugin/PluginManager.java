package com.xtoxray.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Installs Lua plugins and runs them only after the user approved that exact file. Runs on the client thread unless noted. */
public final class PluginManager {
    private static final PluginManager INSTANCE = new PluginManager();
    private static final Logger LOGGER = LoggerFactory.getLogger("XtoXray-Plugins");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record RejectedFile(Path file, String reason) {
    }

    /** What the user agreed to for one exact file. */
    private static final class Trust {
        String sha256;
        boolean enabled;
    }

    private final List<ExternalPlugin> plugins = new ArrayList<>();
    private final List<RejectedFile> rejected = new ArrayList<>();
    private Map<String, Trust> trust = new HashMap<>();
    private final MinecraftHost host = new MinecraftHost();
    private Path root;
    private PluginRepository repository;
    private volatile RepositoryIndex index;
    private volatile Map<String, Long> downloads = Map.of();
    private volatile String repositoryStatus = "Loading plugin list...";

    private PluginManager() {
    }

    public static PluginManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        root = FabricLoader.getInstance().getGameDir().resolve("xtoxray");
        repository = new PluginRepository(root.resolve("repo-cache"));
        loadTrust();
        index = repository.loadCached();
        downloads = repository.loadCachedDownloads();
        rescan();
        PluginFolderWatcher.start(pluginsFolder());
        refreshRepository();
    }

    public Path pluginsFolder() {
        return root.resolve("plugins");
    }

    public List<ExternalPlugin> getExternalPlugins() { return List.copyOf(plugins); }
    public List<RejectedFile> getRejected() { return List.copyOf(rejected); }
    public String getRepositoryStatus() { return repositoryStatus; }

    public List<PluginDefinition> getAvailable() {
        RepositoryIndex current = index;
        if (current == null || current.isExpired(Instant.now())) return List.of();
        Set<String> installed = plugins.stream().map(p -> p.getDefinition().id()).collect(Collectors.toSet());
        return current.plugins().stream()
            .filter(p -> !installed.contains(p.id()) && !current.revoked().contains(p.sha256()))
            .toList();
    }

    /** Everything in the signed list that is not revoked, including installed plugins. */
    public List<PluginDefinition> getCatalog() {
        RepositoryIndex current = index;
        if (current == null || current.isExpired(Instant.now())) return List.of();
        return current.plugins().stream().filter(p -> !current.revoked().contains(p.sha256())).toList();
    }

    public long downloadsOf(String id) {
        return downloads.getOrDefault(id, -1L);
    }

    public ExternalPlugin installedById(String id) {
        return plugins.stream().filter(p -> p.getDefinition().id().equals(id)).findFirst().orElse(null);
    }

    public void rescan() {
        rejected.clear();
        List<ExternalPlugin> previous = new ArrayList<>(plugins);
        plugins.clear();
        Set<String> ids = new HashSet<>();
        try {
            Files.createDirectories(pluginsFolder());
            List<Path> files;
            try (Stream<Path> stream = Files.list(pluginsFolder())) {
                files = stream.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase();
                if (name.endsWith(".jar")) {
                    rejected.add(new RejectedFile(file, "Jar files are not allowed, plugins must be Lua"));
                } else if (name.endsWith(".lua")) {
                    try {
                        if (Files.size(file) > PluginDefinition.MAX_BYTES) throw new PluginLoadException("Plugin file is too large");
                        PluginDefinition definition = PluginDefinition.parse(Files.readString(file, StandardCharsets.UTF_8));
                        if (!ids.add(definition.id())) throw new PluginLoadException("Duplicate plugin id: " + definition.id());
                        ExternalPlugin same = previous.stream().filter(p -> p.getSourceFile().equals(file)
                            && p.getDefinition().sha256().equals(definition.sha256())).findFirst().orElse(null);
                        if (same != null) previous.remove(same);
                        plugins.add(same != null ? same : new ExternalPlugin(definition, file));
                    } catch (IOException | PluginLoadException e) {
                        LOGGER.warn("Rejected plugin {}: {}", file.getFileName(), e.getMessage());
                        rejected.add(new RejectedFile(file, e.getMessage()));
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan plugins folder", e);
        }
        previous.forEach(this::stop);
        applyState();
    }

    private void applyState() {
        RepositoryIndex current = index;
        for (ExternalPlugin plugin : plugins) {
            Trust entry = trust.get(plugin.getDefinition().id());
            if (current != null && current.revoked().contains(plugin.getDefinition().sha256())) {
                stop(plugin);
                plugin.setStatus(ExternalPlugin.Status.REVOKED);
            } else if (entry == null || !plugin.getDefinition().sha256().equals(entry.sha256)) {
                stop(plugin);
                plugin.setStatus(ExternalPlugin.Status.NEEDS_APPROVAL);
            } else if (entry.enabled) {
                if (plugin.getStatus() == ExternalPlugin.Status.CRASHED) continue;
                plugin.setStatus(ExternalPlugin.Status.ENABLED);
                start(plugin);
            } else {
                stop(plugin);
                plugin.setStatus(ExternalPlugin.Status.DISABLED);
            }
        }
    }

    private void start(ExternalPlugin plugin) {
        if (plugin.getRuntime() != null) return;
        PluginDefinition definition = plugin.getDefinition();
        LuaPlugin runtime = new LuaPlugin(definition, host, root.resolve("plugin-data").resolve(definition.id()));
        String error = runtime.start();
        if (error != null) {
            LOGGER.error("Plugin {} failed to start: {}", definition.id(), error);
            plugin.setStatus(ExternalPlugin.Status.CRASHED);
            return;
        }
        plugin.setRuntime(runtime);
    }

    private void stop(ExternalPlugin plugin) {
        LuaPlugin runtime = plugin.getRuntime();
        if (runtime != null) runtime.saveSettings();
        plugin.setRuntime(null);
    }

    /** Sends a game event to all running plugins. */
    public void fire(String event, LuaValue... args) {
        for (ExternalPlugin plugin : plugins) {
            LuaPlugin runtime = plugin.getRuntime();
            if (runtime == null) continue;
            runtime.fire(event, args);
            if (runtime.isStopped()) {
                stop(plugin);
                plugin.setStatus(ExternalPlugin.Status.CRASHED);
            }
        }
    }

    public void fireRender() {
        host.beginFrame();
        fire("render");
    }

    /** Running plugins that declared settings; each gets a tab in the menu. */
    public List<LuaPlugin> getSettingsPlugins() {
        return plugins.stream().map(ExternalPlugin::getRuntime).filter(r -> r != null && !r.widgets().isEmpty()).toList();
    }

    public void saveAllSettings() {
        plugins.stream().map(ExternalPlugin::getRuntime).filter(r -> r != null).forEach(LuaPlugin::saveSettings);
    }

    public void approve(ExternalPlugin plugin) {
        Trust entry = new Trust();
        entry.sha256 = plugin.getDefinition().sha256();
        entry.enabled = true;
        trust.put(plugin.getDefinition().id(), entry);
        saveTrust();
        applyState();
    }

    public void setEnabled(ExternalPlugin plugin, boolean enabled) {
        Trust entry = trust.get(plugin.getDefinition().id());
        if (entry == null) return;
        entry.enabled = enabled;
        saveTrust();
        if (enabled && plugin.getStatus() == ExternalPlugin.Status.CRASHED) plugin.setStatus(ExternalPlugin.Status.DISABLED);
        applyState();
    }

    public void uninstall(ExternalPlugin plugin) {
        stop(plugin);
        trust.remove(plugin.getDefinition().id());
        saveTrust();
        deleteFile(plugin.getSourceFile());
        plugins.remove(plugin);
        applyState();
    }

    public void deleteRejected(RejectedFile file) {
        deleteFile(file.file());
        rejected.remove(file);
    }

    private static void deleteFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.error("Failed to delete {}", file, e);
        }
    }

    /** @return null on success, otherwise the error */
    public String install(PluginDefinition definition) {
        try {
            Files.createDirectories(pluginsFolder());
            Path temp = Files.createDirectories(root.resolve("repo-cache")).resolve(definition.id() + ".download");
            Files.writeString(temp, definition.source(), StandardCharsets.UTF_8);
            Files.move(temp, pluginsFolder().resolve(definition.id() + ".lua"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to install plugin {}", definition.id(), e);
            return e.getMessage();
        }
        Thread counter = new Thread(() -> repository.countDownload(definition), "XtoXray-PluginCount");
        counter.setDaemon(true);
        counter.start();
        rescan();
        plugins.stream().filter(p -> p.getDefinition().sha256().equals(definition.sha256())).findFirst().ifPresent(this::approve);
        return null;
    }

    /** Safe from any thread: network work happens in the background. */
    public void refreshRepository() {
        Thread thread = new Thread(() -> {
            try {
                try {
                    downloads = repository.fetchDownloads();
                } catch (IOException | PluginLoadException | RuntimeException e) {
                    LOGGER.info("Download counts unavailable: {}", e.getMessage());
                }
                RepositoryIndex cached = index;
                RepositoryIndex fresh = repository.fetch(cached == null ? 0 : cached.sequence());
                Minecraft.getInstance().execute(() -> {
                    index = fresh;
                    repositoryStatus = fresh.isExpired(Instant.now()) ? "Plugin list has expired" : "";
                    applyState();
                });
            } catch (IOException | PluginLoadException e) {
                LOGGER.warn("Could not update plugin list: {}", e.getMessage());
                repositoryStatus = "Plugin list unavailable";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "XtoXray-PluginRepo");
        thread.setDaemon(true);
        thread.start();
    }

    private Path trustFile() {
        return root.resolve("plugin-trust.json");
    }

    private void loadTrust() {
        try {
            if (Files.exists(trustFile())) {
                Map<String, Trust> stored = GSON.fromJson(Files.readString(trustFile()), new TypeToken<Map<String, Trust>>() { }.getType());
                if (stored != null) trust = new HashMap<>(stored);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not read plugin approvals; all plugins need approval again", e);
        }
    }

    private void saveTrust() {
        try {
            Files.createDirectories(root);
            Files.writeString(trustFile(), GSON.toJson(trust));
        } catch (IOException e) {
            LOGGER.error("Could not save plugin approvals", e);
        }
    }
}
