package com.xtoxray.plugin;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

/** Rescans on folder changes. New files only appear as "needs approval". */
public final class PluginFolderWatcher {
    private static boolean started;

    private PluginFolderWatcher() {
    }

    public static synchronized void start(Path folder) {
        if (started) return;
        started = true;
        Thread thread = new Thread(() -> run(folder), "XtoXray-PluginWatcher");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(Path folder) {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            Files.createDirectories(folder);
            folder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            while (true) {
                WatchKey key = watcher.poll(2, TimeUnit.SECONDS);
                if (key == null) continue;
                boolean changed = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context() instanceof Path path && path.toString().endsWith(".lua")) changed = true;
                }
                key.reset();
                if (changed) Minecraft.getInstance().execute(PluginManager.getInstance()::rescan);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LoggerFactory.getLogger("XtoXray-Plugins").warn("Plugin folder watcher stopped", e);
        }
    }
}
