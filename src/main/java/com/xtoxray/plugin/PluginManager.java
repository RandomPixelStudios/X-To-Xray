package com.xtoxray.plugin;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.PluginLogger;
import com.xtoxray.api.plugin.event.EventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PluginManager {
    private static final PluginManager INSTANCE = new PluginManager();
    private static final Logger LOGGER = LoggerFactory.getLogger("XtoXray-Plugins");

    private final List<ExternalPlugin> externalPlugins = new ArrayList<>();
    private final Map<String, Plugin> builtinPlugins = new LinkedHashMap<>();
    private final Map<String, Plugin> sidebarTabs = new LinkedHashMap<>();
    private final EventBus eventBus = new EventBus();
    private boolean loaded = false;

    private PluginManager() {}

    public static PluginManager getInstance() { return INSTANCE; }

    public void initBuiltin() {
        PluginState state = PluginState.getInstance();
        for (PluginData data : state.getPlugins()) {
            builtinPlugins.put(data.getName(), new BuiltinPluginWrapper(data));
        }
    }

    public void loadExternalPlugins(Path pluginsFolder, PluginContext context) {
        externalPlugins.clear();
        if (!Files.exists(pluginsFolder)) {
            try { Files.createDirectories(pluginsFolder); } catch (Exception e) { return; }
        }
        try (Stream<Path> stream = Files.list(pluginsFolder)) {
            List<Path> jars = stream.filter(p -> p.toString().endsWith(".jar")).collect(Collectors.toList());
            for (Path jar : jars) {
                try {
                    ExternalPlugin plugin = PluginLoader.loadPlugin(jar, context);
                    externalPlugins.add(plugin);
                    LOGGER.info("Loaded external plugin: {} v{}", plugin.getDescriptor().getName(), plugin.getDescriptor().getVersion());
                } catch (PluginLoadException e) {
                    LOGGER.error("Failed to load plugin {}: {}", jar.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to scan plugins folder", e);
        }
        loaded = true;
    }

    public boolean enablePlugin(String name) {
        Plugin plugin = builtinPlugins.get(name);
        ExternalPlugin extPlugin = null;
        if (plugin == null) {
            for (ExternalPlugin ext : externalPlugins) {
                if (ext.getDescriptor().getName().equals(name)) {
                    plugin = ext.getInstance();
                    extPlugin = ext;
                    break;
                }
            }
        }
        if (plugin == null) return false;
        try {
            PluginContext ctx = new SimplePluginContext(name, eventBus);
            plugin.onEnable(ctx);
            if (extPlugin != null) {
                extPlugin.setEnabled(true);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to enable plugin " + name, e);
            return false;
        }
    }

    public boolean disablePlugin(String name) {
        Plugin plugin = builtinPlugins.get(name);
        ExternalPlugin extPlugin = null;
        if (plugin == null) {
            for (ExternalPlugin ext : externalPlugins) {
                if (ext.getDescriptor().getName().equals(name)) {
                    plugin = ext.getInstance();
                    extPlugin = ext;
                    break;
                }
            }
        }
        if (plugin == null) return false;
        try {
            PluginContext ctx = new SimplePluginContext(name, eventBus);
            plugin.onDisable(ctx);
            if (extPlugin != null) {
                extPlugin.setEnabled(false);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to disable plugin " + name, e);
            return false;
        }
    }

    public void registerSidebarTab(Plugin plugin, String tabName) {
        if (plugin == null || tabName == null || tabName.isEmpty()) return;
        sidebarTabs.put(tabName, plugin);
    }

    public Map<String, Plugin> getSidebarTabs() { return sidebarTabs; }

    public List<ExternalPlugin> getExternalPlugins() { return externalPlugins; }
    public Map<String, Plugin> getBuiltinPlugins() { return builtinPlugins; }
    public EventBus getEventBus() { return eventBus; }
    public boolean isLoaded() { return loaded; }

    private static class SimplePluginContext implements PluginContext {
        private final String name;
        private final EventBus bus;
        private final Path dataFolder;
        private final org.slf4j.Logger logger;

        SimplePluginContext(String name, EventBus bus) {
            this.name = name;
            this.bus = bus;
            Path gameDir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath();
            this.dataFolder = gameDir.resolve("xtoxray").resolve("plugins").resolve(name);
            this.logger = LoggerFactory.getLogger("XtoXray-" + name);
        }

        @Override
        public PluginLogger getLogger() {
            return new PluginLogger() {
                @Override
                public void info(String message) { logger.info(message); }
                @Override
                public void warn(String message) { logger.warn(message); }
                @Override
                public void error(String message) { logger.error(message); }
            };
        }

        @Override
        public Path getDataFolder() { return dataFolder; }

        @Override
        public EventBus getEventBus() { return bus; }
    }
}