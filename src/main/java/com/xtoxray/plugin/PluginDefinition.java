package com.xtoxray.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/** A Lua plugin file. Its JSON header is read without running any of the script. */
public record PluginDefinition(String id, String name, String version, String description, String author,
                               java.util.List<String> categories, Set<PluginPermission> permissions, String source, String sha256) {
    public static final int FORMAT = 1;
    public static final int MAX_BYTES = 256 * 1024;
    private static final String HEADER_START = "--[[xtoxray";
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{1,31}");
    private static final Set<String> KEYS = Set.of("format", "id", "name", "version", "description", "author", "permissions", "categories");

    public static PluginDefinition parse(String source) throws PluginLoadException {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new PluginLoadException("Plugin file is too large");
        int end = source.indexOf("]]");
        if (!source.startsWith(HEADER_START) || end < 0) throw new PluginLoadException("Missing plugin header");
        try {
            JsonObject root = JsonParser.parseString(source.substring(HEADER_START.length(), end)).getAsJsonObject();
            for (String key : root.keySet()) {
                if (!KEYS.contains(key)) throw new PluginLoadException("Unknown field: " + clean(key));
            }
            if (root.get("format").getAsInt() != FORMAT) throw new PluginLoadException("Needs a newer X To Xray");
            String id = root.get("id").getAsString();
            if (!ID.matcher(id).matches()) throw new PluginLoadException("Invalid id");
            Set<PluginPermission> permissions = EnumSet.noneOf(PluginPermission.class);
            if (root.has("permissions")) {
                for (JsonElement p : root.getAsJsonArray("permissions")) permissions.add(PluginPermission.byKey(p.getAsString()));
            }
            java.util.List<String> categories = new java.util.ArrayList<>();
            if (root.has("categories")) {
                for (JsonElement c : root.getAsJsonArray("categories")) {
                    if (categories.size() < 4) categories.add(clean(c.getAsString()));
                }
            }
            String name = clean(root.get("name").getAsString());
            String version = clean(root.get("version").getAsString());
            if (name.isEmpty() || version.isEmpty()) throw new PluginLoadException("Missing name or version");
            return new PluginDefinition(id, name, version,
                root.has("description") ? clean(root.get("description").getAsString()) : "",
                root.has("author") ? clean(root.get("author").getAsString()) : "Unknown",
                java.util.List.copyOf(categories), Set.copyOf(permissions), source, sha256(bytes));
        } catch (RuntimeException e) {
            throw new PluginLoadException("Invalid plugin header", e);
        }
    }

    /** Removes formatting codes and limits length, so text cannot imitate the UI. */
    public static String clean(String text) {
        String stripped = text.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "");
        return stripped.length() > 80 ? stripped.substring(0, 80) : stripped;
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
