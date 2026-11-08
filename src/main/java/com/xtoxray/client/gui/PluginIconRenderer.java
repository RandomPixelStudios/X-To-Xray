package com.xtoxray.client.gui;

import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class PluginIconRenderer {
    private static final Map<String, Identifier> ICONS = Map.of(
        "veinminer", Identifier.parse("xtoxray:veinminer_icon"),
        "hitboxes", Identifier.parse("xtoxray:hitboxes_icon")
    );
    private static Object pipeline;
    private static Method blitSprite;

    public static boolean hasIcon(String id) {
        return ICONS.containsKey(id);
    }

    /** The render pipeline class moved in 26.3, so it is looked up at runtime to fit every 26.x version. */
    public static void render(GuiGraphicsExtractor graphics, String id, int x, int y, int size) {
        try {
            if (blitSprite == null) {
                Class<?> pipelines;
                try {
                    pipelines = Class.forName("net.minecraft.client.renderer.RenderPipelines");
                } catch (ClassNotFoundException moved) {
                    pipelines = Class.forName("net.minecraft.client.renderer.pipeline.RenderPipelines");
                }
                var field = pipelines.getField("GUI_TEXTURED");
                pipeline = field.get(null);
                blitSprite = GuiGraphicsExtractor.class.getMethod("blitSprite", field.getType(), Identifier.class, int.class, int.class, int.class, int.class);
            }
            blitSprite.invoke(graphics, pipeline, ICONS.get(id), x, y, size, size);
        } catch (ReflectiveOperationException e) {
            com.xtoxray.XtoXray.LOGGER.warn("Could not draw plugin icon", e);
        }
    }
}
