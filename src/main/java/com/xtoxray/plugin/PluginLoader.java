package com.xtoxray.plugin;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.PluginDescriptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class PluginLoader {

    public static ExternalPlugin loadPlugin(Path jarFile, PluginContext context) throws PluginLoadException {
        try {
            URL jarUrl = jarFile.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, PluginLoader.class.getClassLoader());

            PluginDescriptor descriptor = readDescriptor(jarFile, classLoader);
            String mainClass = descriptor.getMainClass();
            Class<?> clazz = Class.forName(mainClass, true, classLoader);
            Plugin plugin = (Plugin) clazz.getDeclaredConstructor().newInstance();

            String tabName = plugin.getSidebarTabName();
            if (tabName != null && !tabName.isEmpty()) {
                PluginManager.getInstance().registerSidebarTab(plugin, tabName);
            }

            return new ExternalPlugin(descriptor, plugin, classLoader, jarFile);
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            PluginManager.getInstance().getEventBus();
            throw new PluginLoadException("Failed to load plugin: " + jarFile.getFileName() + " | " + sw.toString(), e);
        }
    }

    private static PluginDescriptor readDescriptor(Path jarFile, URLClassLoader classLoader) throws PluginLoadException {
        try (InputStream is = classLoader.getResourceAsStream("plugin.properties")) {
            if (is != null) {
                return readProperties(is, jarFile);
            }
        } catch (Exception e) {
        }

        try (InputStream is = classLoader.getResourceAsStream("plugin.yml")) {
            if (is != null) {
                return readYaml(is, jarFile);
            }
        } catch (Exception e) {
        }

        throw new PluginLoadException("Missing plugin.properties or plugin.yml in " + jarFile.getFileName());
    }

    private static PluginDescriptor readProperties(InputStream is, Path jarFile) throws PluginLoadException {
        try {
            java.util.Properties props = new java.util.Properties();
            props.load(is);
            String name = props.getProperty("name", "Unknown");
            String version = props.getProperty("version", "1.0");
            String desc = props.getProperty("description", "");
            String author = props.getProperty("author", "Unknown");
            String main = props.getProperty("mainClass");
            if (main == null || main.isEmpty()) {
                throw new PluginLoadException("Missing mainClass in plugin.properties");
            }
            return new PluginDescriptor(name, version, desc, author, main);
        } catch (Exception e) {
            throw new PluginLoadException("Failed to read plugin.properties from " + jarFile.getFileName(), e);
        }
    }

    private static PluginDescriptor readYaml(InputStream is, Path jarFile) throws PluginLoadException {
        try {
            Map<String, String> values = new LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String key = line.substring(0, colon).trim();
                        String val = line.substring(colon + 1).trim();
                        if (val.length() >= 2 && val.charAt(0) == '"' && val.charAt(val.length() - 1) == '"') {
                            val = val.substring(1, val.length() - 1);
                        }
                        values.put(key, val);
                    }
                }
            }
            String name = values.getOrDefault("name", "Unknown");
            String version = values.getOrDefault("version", "1.0");
            String desc = values.getOrDefault("description", "");
            String author = values.getOrDefault("author", "Unknown");
            String main = values.get("mainClass");
            if (main == null || main.isEmpty()) {
                throw new PluginLoadException("Missing mainClass in plugin.yml");
            }
            return new PluginDescriptor(name, version, desc, author, main);
        } catch (Exception e) {
            throw new PluginLoadException("Failed to read plugin.yml from " + jarFile.getFileName(), e);
        }
    }
}