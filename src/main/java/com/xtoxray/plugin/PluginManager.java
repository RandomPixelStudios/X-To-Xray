package com.xtoxray.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xtoxray.ConfigManager;
import com.xtoxray.XrayState;
import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.PluginDescriptor;
import com.xtoxray.api.plugin.PluginLogger;
import com.xtoxray.api.plugin.PluginPermission;
import com.xtoxray.api.plugin.PluginStorage;
import com.xtoxray.api.plugin.XrayAccess;
import com.xtoxray.api.plugin.event.EventBus;
import com.xtoxray.api.plugin.event.PluginEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Plugin lifecycle. All methods run on the client thread unless noted. No plugin code runs before approval. */
public final class PluginManager {
    private static final PluginManager INSTANCE = new PluginManager();
    private static final Logger LOGGER = LoggerFactory.getLogger("XtoXray-Plugins");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ERRORS = 5;
    private static final Set<String> RESERVED_TABS = Set.of("Xray", "Plugins", "Veinminer", "Hitboxes", "Bug Report");

    public record RejectedJar(Path file, String reason) {
    }

    /** What the user agreed to for one exact jar. */
    private static final class Trust {
        String sha256;
        boolean enabled;
        Set<String> permissions = new TreeSet<>();
    }

    private final List<ExternalPlugin> plugins = new ArrayList<>();
    private final List<RejectedJar> rejected = new ArrayList<>();
    private final Map<String, Plugin> sidebarTabs = new LinkedHashMap<>();
    private final PluginEventBus eventBus = new PluginEventBus(this::onPluginError);
    private Map<String, Trust> trust = new HashMap<>();
    private Path root;
    private PluginRepository repository;
    private volatile RepositoryIndex index;
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
        rescan();
        PluginFolderWatcher.start(pluginsFolder());
        refreshRepository();
    }

    public Path pluginsFolder() {
        return root.resolve("plugins");
    }

    public void post(PluginEvent event) {
        eventBus.post(event);
    }

    public List<ExternalPlugin> getExternalPlugins() { return List.copyOf(plugins); }
    public List<RejectedJar> getRejected() { return List.copyOf(rejected); }
    public Map<String, Plugin> getSidebarTabs() { return sidebarTabs; }
    public String getRepositoryStatus() { return repositoryStatus; }

    /** Plugins offered for download: signed, not expired, compatible, not revoked and not installed. */
    public List<RepositoryIndex.Entry> getAvailable() {
        RepositoryIndex current = index;
        if (current == null || current.isExpired(Instant.now())) return List.of();
        Set<String> installed = plugins.stream().map(p -> p.getDescriptor().getId()).collect(Collectors.toSet());
        return current.plugins().stream()
            .filter(e -> e.apiVersion() == Plugin.API_VERSION && !installed.contains(e.id()) && !current.revoked().contains(e.sha256()))
            .toList();
    }

    /** Syncs with the plugins folder. Unchanged jars keep running. */
    public void rescan() {
        rejected.clear();
        Map<Path, ExternalPlugin> loaded = new HashMap<>();
        plugins.forEach(p -> loaded.put(p.getSourceFile(), p));
        List<ExternalPlugin> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try {
            Files.createDirectories(pluginsFolder());
            List<Path> jars;
            try (Stream<Path> files = Files.list(pluginsFolder())) {
                jars = files.filter(p -> p.toString().endsWith(".jar") && Files.isRegularFile(p)).sorted().toList();
            }
            for (Path file : jars) {
                try {
                    if (Files.size(file) > PluginJar.MAX_JAR_BYTES) throw new PluginLoadException("Jar is too large");
                    PluginJar jar = PluginJar.read(Files.readAllBytes(file));
                    if (!ids.add(jar.descriptor().getId())) throw new PluginLoadException("Duplicate plugin id: " + jar.descriptor().getId());
                    ExternalPlugin existing = loaded.get(file);
                    if (existing != null && existing.getJar().sha256().equals(jar.sha256())) {
                        result.add(existing);
                        loaded.remove(file);
                    } else {
                        result.add(new ExternalPlugin(jar, file));
                    }
                } catch (IOException | PluginLoadException e) {
                    LOGGER.warn("Rejected plugin {}: {}", file.getFileName(), e.getMessage());
                    rejected.add(new RejectedJar(file, e.getMessage()));
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan plugins folder", e);
            return;
        }
        loaded.values().forEach(this::stop);
        plugins.clear();
        plugins.addAll(result);
        plugins.forEach(this::applyTrust);
    }

    private void applyTrust(ExternalPlugin plugin) {
        if (plugin.getInstance() != null) {
            if (isRevoked(plugin)) {
                stop(plugin);
                plugin.setStatus(ExternalPlugin.Status.REVOKED);
            }
            return;
        }
        if (plugin.getStatus() == ExternalPlugin.Status.FAILED) return;
        Trust entry = trust.get(plugin.getDescriptor().getId());
        if (isRevoked(plugin)) {
            plugin.setStatus(ExternalPlugin.Status.REVOKED);
        } else if (entry == null || !plugin.getJar().sha256().equals(entry.sha256) || !entry.permissions.containsAll(permissionKeys(plugin))) {
            plugin.setStatus(ExternalPlugin.Status.NEEDS_APPROVAL);
        } else if (entry.enabled) {
            start(plugin);
        } else {
            plugin.setStatus(ExternalPlugin.Status.DISABLED);
        }
    }

    private boolean isRevoked(ExternalPlugin plugin) {
        RepositoryIndex current = index;
        return current != null && current.revoked().contains(plugin.getJar().sha256());
    }

    private static Set<String> permissionKeys(ExternalPlugin plugin) {
        return plugin.getDescriptor().getPermissions().stream().map(PluginPermission::key).collect(Collectors.toCollection(TreeSet::new));
    }

    public void approve(ExternalPlugin plugin) {
        if (isRevoked(plugin)) return;
        Trust entry = new Trust();
        entry.sha256 = plugin.getJar().sha256();
        entry.enabled = true;
        entry.permissions = permissionKeys(plugin);
        trust.put(plugin.getDescriptor().getId(), entry);
        saveTrust();
        start(plugin);
    }

    public void setEnabled(ExternalPlugin plugin, boolean enabled) {
        Trust entry = trust.get(plugin.getDescriptor().getId());
        if (entry == null || plugin.getStatus() == ExternalPlugin.Status.REVOKED) return;
        entry.enabled = enabled;
        saveTrust();
        if (enabled) {
            start(plugin);
        } else {
            stop(plugin);
            plugin.setStatus(ExternalPlugin.Status.DISABLED);
        }
    }

    public void uninstall(ExternalPlugin plugin) {
        stop(plugin);
        trust.remove(plugin.getDescriptor().getId());
        saveTrust();
        deleteFile(plugin.getSourceFile());
        plugins.remove(plugin);
    }

    public void deleteRejected(RejectedJar jar) {
        deleteFile(jar.file());
        rejected.remove(jar);
    }

    private static void deleteFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.error("Failed to delete {}", file, e);
        }
    }

    private void start(ExternalPlugin plugin) {
        if (plugin.getInstance() != null) return;
        PluginDescriptor descriptor = plugin.getDescriptor();
        try {
            PluginClassLoader loader = new PluginClassLoader(descriptor.getId(), plugin.getJar().classes(), PluginManager.class.getClassLoader());
            Class<?> main = Class.forName(descriptor.getMainClass(), true, loader);
            Plugin instance = (Plugin) main.getDeclaredConstructor().newInstance();
            plugin.setInstance(instance);
            plugin.resetErrors();
            instance.onEnable(new Context(plugin));
            plugin.setStatus(ExternalPlugin.Status.ENABLED);
            String tab = instance.getSidebarTabName();
            if (tab != null) {
                String cleanTab = PluginJar.clean(tab);
                if (!cleanTab.isEmpty() && !RESERVED_TABS.contains(cleanTab) && !sidebarTabs.containsKey(cleanTab)) {
                    sidebarTabs.put(cleanTab, instance);
                }
            }
            LOGGER.info("Enabled plugin {} v{}", descriptor.getId(), descriptor.getVersion());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError | StackOverflowError e) {
            LOGGER.error("Failed to enable plugin {}", descriptor.getId(), e);
            fail(plugin);
        }
    }

    private void stop(ExternalPlugin plugin) {
        Plugin instance = plugin.getInstance();
        if (instance == null) return;
        try {
            instance.onDisable(new Context(plugin));
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            LOGGER.error("Plugin {} failed while disabling", plugin.getDescriptor().getId(), e);
        }
        eventBus.removeOwner(plugin);
        sidebarTabs.values().removeIf(p -> p == instance);
        plugin.setInstance(null);
    }

    private void fail(ExternalPlugin plugin) {
        stop(plugin);
        plugin.setStatus(ExternalPlugin.Status.FAILED);
    }

    private void onPluginError(Object owner, Throwable error) {
        ExternalPlugin plugin = (ExternalPlugin) owner;
        LOGGER.error("Plugin {} threw an error", plugin.getDescriptor().getId(), error);
        if (plugin.addError() >= MAX_ERRORS) {
            LOGGER.error("Stopping plugin {} after {} errors", plugin.getDescriptor().getId(), MAX_ERRORS);
            fail(plugin);
        }
    }

    public String[] sidebarContent(Plugin instance) {
        try {
            String[] lines = instance.getSidebarTabContent();
            if (lines == null) return new String[0];
            return Stream.of(lines).filter(l -> l != null).limit(30).map(PluginJar::clean).toArray(String[]::new);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            plugins.stream().filter(p -> p.getInstance() == instance).findFirst().ifPresent(p -> onPluginError(p, e));
            return new String[0];
        }
    }

    /** Safe from any thread: network work happens in the background. */
    public void refreshRepository() {
        Thread thread = new Thread(() -> {
            try {
                RepositoryIndex cached = index;
                RepositoryIndex fresh = repository.fetch(cached == null ? 0 : cached.sequence());
                onClientThread(() -> {
                    index = fresh;
                    repositoryStatus = fresh.isExpired(Instant.now()) ? "Plugin list has expired" : "";
                    plugins.forEach(this::applyTrust);
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

    /** Downloads, verifies and approves a plugin. onDone gets null on success, otherwise the error. */
    public void install(RepositoryIndex.Entry entry, Consumer<String> onDone) {
        Thread thread = new Thread(() -> {
            String error = null;
            byte[] bytes = null;
            try {
                bytes = repository.downloadPlugin(entry);
                PluginDescriptor descriptor = PluginJar.read(bytes).descriptor();
                Set<String> declared = descriptor.getPermissions().stream().map(PluginPermission::key).collect(Collectors.toSet());
                if (!descriptor.getId().equals(entry.id()) || !declared.equals(Set.copyOf(entry.permissions()))) {
                    throw new PluginLoadException("Plugin does not match the repository listing");
                }
                Path temp = Files.createDirectories(root.resolve("repo-cache")).resolve(entry.id() + ".download");
                Files.write(temp, bytes);
                Files.createDirectories(pluginsFolder());
                Files.move(temp, pluginsFolder().resolve(entry.id() + ".jar"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | PluginLoadException e) {
                LOGGER.error("Failed to install plugin {}", entry.id(), e);
                error = e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error = "Interrupted";
            }
            String result = error;
            onClientThread(() -> {
                if (result == null) {
                    rescan();
                    plugins.stream().filter(p -> p.getDescriptor().getId().equals(entry.id())
                            && p.getJar().sha256().equalsIgnoreCase(entry.sha256()))
                        .findFirst().ifPresent(this::approve);
                }
                onDone.accept(result);
            });
        }, "XtoXray-PluginInstall");
        thread.setDaemon(true);
        thread.start();
    }

    private static void onClientThread(Runnable task) {
        Minecraft.getInstance().execute(task);
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

    private final class Context implements PluginContext {
        private final ExternalPlugin plugin;
        private final PluginLogger logger;
        private final PluginStorage storage;

        Context(ExternalPlugin plugin) {
            this.plugin = plugin;
            Logger slf4j = LoggerFactory.getLogger("XtoXray-" + plugin.getDescriptor().getId());
            this.logger = new PluginLogger() {
                @Override public void info(String message) { slf4j.info("{}", message); }
                @Override public void warn(String message) { slf4j.warn("{}", message); }
                @Override public void error(String message) { slf4j.error("{}", message); }
            };
            this.storage = new PluginStorageImpl(root.resolve("plugin-data").resolve(plugin.getDescriptor().getId()));
        }

        private void require(PluginPermission permission) {
            if (!plugin.getDescriptor().getPermissions().contains(permission)) {
                throw new SecurityException("Plugin did not declare permission " + permission.key());
            }
        }

        @Override public PluginDescriptor getDescriptor() { return plugin.getDescriptor(); }
        @Override public PluginLogger getLogger() { return logger; }
        @Override public EventBus getEventBus() { return eventBus.scopedTo(plugin); }

        @Override
        public PluginStorage getStorage() {
            require(PluginPermission.STORAGE);
            return storage;
        }

        @Override
        public XrayAccess getXray() {
            require(PluginPermission.XRAY_READ);
            return new XrayAccess() {
                @Override
                public boolean isActive() {
                    return XrayState.getInstance().isActive();
                }

                @Override
                public Set<String> getWhitelist() {
                    return XrayState.getInstance().getWhitelist().stream()
                        .map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).collect(Collectors.toUnmodifiableSet());
                }

                @Override
                public boolean addToWhitelist(String blockId) {
                    return changeWhitelist(blockId, true);
                }

                @Override
                public boolean removeFromWhitelist(String blockId) {
                    return changeWhitelist(blockId, false);
                }

                private boolean changeWhitelist(String blockId, boolean add) {
                    require(PluginPermission.XRAY_WHITELIST);
                    Identifier id = Identifier.tryParse(blockId);
                    if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return false;
                    Block block = BuiltInRegistries.BLOCK.getValue(id);
                    if (add) XrayState.getInstance().addToWhitelist(block);
                    else XrayState.getInstance().removeFromWhitelist(block);
                    ConfigManager.save();
                    if (XrayState.getInstance().isActive()) {
                        com.xtoxray.client.XrayToggleHandler.rebuildAllSections(Minecraft.getInstance());
                    }
                    return true;
                }
            };
        }
    }
}
