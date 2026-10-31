package com.xtoxray.plugin;

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

/** The signed plugin list, including the plugins themselves. Nothing is read before the Ed25519 signature is checked. */
public record RepositoryIndex(long sequence, Instant expires, List<PluginDefinition> plugins, Set<String> revoked) {
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
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            List<PluginDefinition> plugins = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("plugins")) {
                try {
                    plugins.add(PluginDefinition.parse(element.getAsString()));
                } catch (PluginLoadException e) {
                    // Written for a newer mod version: simply not offered here.
                }
            }
            Set<String> revoked = new HashSet<>();
            root.getAsJsonArray("revoked").forEach(r -> revoked.add(r.getAsString()));
            index = new RepositoryIndex(root.get("sequence").getAsLong(), Instant.parse(root.get("expires").getAsString()),
                List.copyOf(plugins), Set.copyOf(revoked));
        } catch (RuntimeException e) {
            throw new PluginLoadException("Malformed repository index", e);
        }
        if (index.sequence() < minSequence) throw new PluginLoadException("Repository index is older than the last one seen");
        return index;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expires);
    }
}
