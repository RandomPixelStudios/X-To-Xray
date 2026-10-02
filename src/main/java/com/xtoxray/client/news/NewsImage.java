package com.xtoxray.client.news;

import com.xtoxray.XtoXray;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

final class NewsImage {
    private static final Map<String, Float> CACHE = new HashMap<>();

    private NewsImage() {
    }

    static synchronized float aspect(String imagePath) {
        Float cached = CACHE.get(imagePath);
        if (cached != null) {
            return cached;
        }
        float value = readAspect(imagePath);
        CACHE.put(imagePath, value);
        return value;
    }

    private static float readAspect(String imagePath) {
        try {
            Identifier id = Identifier.parse(imagePath);
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return 16.0f / 9.0f;
            }
            try (InputStream in = resource.get().open();
                 DataInputStream data = new DataInputStream(in)) {
                byte[] signature = new byte[8];
                data.readFully(signature);
                data.readInt();
                int type = data.readInt();
                if (type != 0x49484452) {
                    return 16.0f / 9.0f;
                }
                int width = data.readInt();
                int height = data.readInt();
                if (width <= 0 || height <= 0) {
                    return 16.0f / 9.0f;
                }
                return (float) width / (float) height;
            }
        } catch (Exception e) {
            XtoXray.LOGGER.warn("Konnte News-Bildgroesse nicht lesen: {}", imagePath, e);
            return 16.0f / 9.0f;
        }
    }
}
