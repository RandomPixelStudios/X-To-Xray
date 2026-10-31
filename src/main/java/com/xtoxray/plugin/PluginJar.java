package com.xtoxray.plugin;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginDescriptor;
import com.xtoxray.api.plugin.PluginPermission;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A plugin jar read fully into memory, so the verified bytes are exactly the bytes that get loaded. */
public record PluginJar(PluginDescriptor descriptor, Map<String, byte[]> classes, String sha256) {
    public static final long MAX_JAR_BYTES = 16L * 1024 * 1024;
    private static final long MAX_UNPACKED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 4096;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{1,31}");
    private static final Pattern MAIN_CLASS = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+");
    private static final Set<String> RESERVED_IDS = Set.of("veinminer", "hitboxes", "xray", "minecraft", "xtoxray");
    private static final List<String> RESERVED_PACKAGES = List.of("java/", "javax/", "jdk/", "sun/", "com/sun/",
        "net/minecraft/", "com/mojang/", "net/fabricmc/", "org/spongepowered/", "org/objectweb/", "com/xtoxray/");

    public static PluginJar read(byte[] jar) throws PluginLoadException {
        if (jar.length > MAX_JAR_BYTES) throw new PluginLoadException("Jar is too large");
        Map<String, byte[]> classes = new HashMap<>();
        byte[] yaml = null;
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new PluginLoadException("Jar has too many entries");
                if (entry.isDirectory()) continue;
                byte[] data = readLimited(zip, MAX_UNPACKED_BYTES - total);
                total += data.length;
                String name = entry.getName();
                if (name.equals("plugin.yml")) {
                    if (yaml != null) throw new PluginLoadException("Duplicate plugin.yml");
                    yaml = data;
                } else if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class")) {
                    String internal = name.substring(0, name.length() - ".class".length());
                    for (String reserved : RESERVED_PACKAGES) {
                        if (internal.startsWith(reserved)) throw new PluginLoadException("Reserved package: " + internal);
                    }
                    if (classes.put(internal, data) != null) throw new PluginLoadException("Duplicate class: " + internal);
                } else if (name.endsWith(".jar") || (name.startsWith("META-INF/versions/") && name.endsWith(".class"))) {
                    throw new PluginLoadException("Nested code is not allowed: " + name);
                }
            }
        } catch (IOException e) {
            throw new PluginLoadException("Not a valid jar", e);
        }
        if (yaml == null) throw new PluginLoadException("Missing plugin.yml");
        PluginDescriptor descriptor = parseDescriptor(new String(yaml, StandardCharsets.UTF_8));
        if (!classes.containsKey(descriptor.getMainClass().replace('.', '/'))) {
            throw new PluginLoadException("mainClass not found in jar: " + descriptor.getMainClass());
        }
        List<String> violations = PluginVerifier.verify(classes);
        if (!violations.isEmpty()) {
            throw new PluginLoadException("Blocked by security check: " + violations.get(0)
                + (violations.size() > 1 ? " (+" + (violations.size() - 1) + " more)" : ""));
        }
        return new PluginJar(descriptor, Map.copyOf(classes), sha256(jar));
    }

    private static byte[] readLimited(InputStream in, long limit) throws IOException, PluginLoadException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            size += read;
            if (size > limit) throw new PluginLoadException("Jar unpacks to too much data");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static PluginDescriptor parseDescriptor(String yaml) throws PluginLoadException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : yaml.split("\\R")) {
            String line = raw.trim();
            int colon = line.indexOf(':');
            if (line.isEmpty() || line.startsWith("#") || colon <= 0) continue;
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(line.substring(0, colon).trim(), value);
        }
        String id = required(values, "id");
        if (!ID.matcher(id).matches()) throw new PluginLoadException("Invalid id (use a-z, 0-9, _ and -): " + id);
        if (RESERVED_IDS.contains(id)) throw new PluginLoadException("Reserved id: " + id);
        String main = required(values, "mainClass");
        if (!MAIN_CLASS.matcher(main).matches()) throw new PluginLoadException("Invalid mainClass: " + main);
        int api;
        try {
            api = Integer.parseInt(required(values, "apiVersion"));
        } catch (NumberFormatException e) {
            throw new PluginLoadException("apiVersion must be a number");
        }
        if (api != Plugin.API_VERSION) {
            throw new PluginLoadException("Needs plugin API " + api + ", this XtoXray has " + Plugin.API_VERSION);
        }
        Set<PluginPermission> permissions = EnumSet.noneOf(PluginPermission.class);
        for (String key : values.getOrDefault("permissions", "").replace("[", "").replace("]", "").split(",")) {
            String trimmed = key.trim();
            if (trimmed.isEmpty()) continue;
            permissions.add(PluginPermission.byKey(trimmed)
                .orElseThrow(() -> new PluginLoadException("Unknown permission: " + trimmed)));
        }
        return new PluginDescriptor(id, clean(required(values, "name")), clean(required(values, "version")),
            clean(values.getOrDefault("description", "")), clean(values.getOrDefault("author", "Unknown")),
            main, api, permissions);
    }

    private static String required(Map<String, String> values, String key) throws PluginLoadException {
        String value = values.get(key);
        if (value == null || value.isEmpty()) throw new PluginLoadException("Missing " + key + " in plugin.yml");
        return value;
    }

    /** Removes formatting codes and limits length, so names cannot imitate UI text. */
    static String clean(String text) {
        String stripped = text.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "");
        return stripped.length() > 80 ? stripped.substring(0, 80) : stripped;
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
