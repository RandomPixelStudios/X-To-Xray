package com.xtoxray.plugin;

import net.minecraft.client.Minecraft;

import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

public class PluginFolderWatcher implements Runnable {
    private static PluginFolderWatcher instance;
    private static boolean running = false;

    private Path pluginsFolder;
    private Set<Path> knownFiles = new HashSet<>();

    private PluginFolderWatcher(Path pluginsFolder) {
        this.pluginsFolder = pluginsFolder;
    }

    public static synchronized void start(Path pluginsFolder) {
        if (running) return;
        if (instance == null || !instance.pluginsFolder.equals(pluginsFolder)) {
            instance = new PluginFolderWatcher(pluginsFolder);
        }
        running = true;
        new Thread(instance, "PluginWatcher").start();
    }

    @Override
    public void run() {
        try {
            if (!Files.exists(pluginsFolder)) {
                Files.createDirectories(pluginsFolder);
            }

            try (var stream = Files.list(pluginsFolder)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .forEach(knownFiles::add);
            }

            WatchService watcher = FileSystems.getDefault().newWatchService();
            pluginsFolder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

            while (running) {
                WatchKey key = watcher.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                if (key == null) continue;

                boolean changed = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    Path fullPath = pluginsFolder.resolve(fileName);

                    if (fullPath.toString().endsWith(".jar") && !knownFiles.contains(fullPath)) {
                        knownFiles.add(fullPath);
                        changed = true;
                    }
                }
                key.reset();

                if (changed) {
                    Minecraft.getInstance().execute(() -> {
                        PluginManager.getInstance().loadExternalPlugins(pluginsFolder, null);
                    });
                }
            }
        } catch (Exception e) {
        }
    }
}