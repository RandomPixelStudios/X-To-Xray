package com.xtoxray.client.gui;

import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class PluginIconRenderer {
    private static final Map<String, Identifier> ICONS = Map.of(
        "veinminer", Identifier.parse("xtoxray:veinminer_icon"),
        "hitboxes", Identifier.parse("xtoxray:hitboxes_icon")
    );

    public static boolean hasIcon(String id) {
        return ICONS.containsKey(id);
    }

    public static void render(GuiGraphicsExtractor graphics, String id, int x, int y, int size) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICONS.get(id), x, y, size, size);
    }
}
