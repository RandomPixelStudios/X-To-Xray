package com.xtoxray.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The signed plugin list. Nothing in it is read before its Ed25519 signature has been checked. */
public record RepositoryIndex(long sequence, Instant expires, List<Entry> plugins, Set<String> revoked) {
    public record Entry(String id, String name, String description, String author, String version,
                        int apiVersion, String url, String sha256, List<String> permissions) {
    }

    public static RepositoryIndex parseSigned(byte[] json, byte[] base64Signature, String base64PublicKey, long minSequence)
            throws PluginLoadException {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey)));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(json);
            byte[] signature = Base64.getDecoder().decode(new String(base64Signature, StandardCharsets.US_ASCII).trim());
            if (!verifier.verify(signature)) throw new PluginLoadException("Invalid repository signature");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new PluginLoadException("Invalid repository signature", e);
        }
        RepositoryIndex index;
        try {
            index = parse(new String(json, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new PluginLoadException("Malformed repository index", e);
        }
        if (index.sequence() < minSequence) throw new PluginLoadException("Repository index is older than the last one seen");
        return index;
    }

    private static RepositoryIndex parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("plugins")) {
            JsonObject o = element.getAsJsonObject();
            List<String> permissions = new ArrayList<>();
            JsonArray array = o.getAsJsonArray("permissions");
            if (array != null) array.forEach(p -> permissions.add(p.getAsString()));
            entries.add(new Entry(o.get("id").getAsString(), PluginJar.clean(o.get("name").getAsString()),
                PluginJar.clean(o.get("description").getAsString()), PluginJar.clean(o.get("author").getAsString()),
                PluginJar.clean(o.get("version").getAsString()), o.get("apiVersion").getAsInt(),
                o.get("url").getAsString(), o.get("sha256").getAsString(), List.copyOf(permissions)));
        }
        Set<String> revoked = new HashSet<>();
        root.getAsJsonArray("revoked").forEach(r -> revoked.add(r.getAsString()));
        return new RepositoryIndex(root.get("sequence").getAsLong(), Instant.parse(root.get("expires").getAsString()),
            List.copyOf(entries), Set.copyOf(revoked));
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expires);
    }
}
