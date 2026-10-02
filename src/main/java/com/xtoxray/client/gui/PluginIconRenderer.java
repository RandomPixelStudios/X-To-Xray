package com.xtoxray.client.gui;

import java.util.Map;
import net.minecraft.resources.Identifier;

public class PluginIconRenderer {
    private static final Map<String, Identifier> ICONS = Map.of(
        "Veinminer", Identifier.parse("xtoxray:veinminer_icon"),
        "Hitboxes", Identifier.parse("xtoxray:hitboxes_icon")
    );

    public static boolean hasIcon(String name) {
        return ICONS.containsKey(name);
    }

    public static void render(Object graphics, String name, int x, int y, int size) {
        SpriteRenderer.draw(graphics, ICONS, name, x, y, size);
    }
}