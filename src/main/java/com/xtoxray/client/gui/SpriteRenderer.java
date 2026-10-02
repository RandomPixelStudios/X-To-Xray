package com.xtoxray.client.gui;

import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.resources.Identifier;

final class SpriteRenderer {
    private static final String[] PIPELINE_CLASSES = {
        "net.minecraft.client.renderer.RenderPipelines"
    };
    private static final String[] PIPELINE_TYPES = {
        "com.mojang.blaze3d.pipeline.RenderPipeline",
        "com.mojang.renderpearl.api.pipeline.RenderPipeline"
    };
    private static final String SPRITE_FIELD = "GUI_TEXTURED";

    private static Object sprite;
    private static Method blitSprite;
    private static boolean resolved;

    private SpriteRenderer() {
    }

    static void draw(Object graphics, Map<String, Identifier> icons, String name, int x, int y, int size) {
        Identifier id = icons.get(name);
        if (id == null || !resolve()) {
            return;
        }
        try {
            blitSprite.invoke(graphics, sprite, id, x, y, size, size);
        } catch (ReflectiveOperationException e) {
            com.xtoxray.XtoXray.LOGGER.warn("Could not draw sprite {}", id, e);
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return blitSprite != null;
        }
        resolved = true;

        Object pipeline = null;
        for (String holder : PIPELINE_CLASSES) {
            Class<?> type = load(holder);
            if (type == null) {
                continue;
            }
            try {
                pipeline = type.getField(SPRITE_FIELD).get(null);
                break;
            } catch (ReflectiveOperationException | LinkageError e) {
                pipeline = null;
            }
        }
        if (pipeline == null) {
            return false;
        }

        Class<?> pipelineType = pipeline.getClass();
        for (String candidate : PIPELINE_TYPES) {
            Class<?> type = load(candidate);
            if (type == null || !type.isAssignableFrom(pipelineType)) {
                continue;
            }
            for (Class<?> graphics : new Class<?>[]{loadGraphics()}) {
                if (graphics == null) {
                    continue;
                }
                try {
                    blitSprite = graphics.getMethod("blitSprite", type, Identifier.class,
                        int.class, int.class, int.class, int.class);
                    sprite = pipeline;
                    return true;
                } catch (NoSuchMethodException | LinkageError e) {
                    blitSprite = null;
                }
            }
        }

        com.xtoxray.XtoXray.LOGGER.warn("No blitSprite overload found for {}", pipelineType.getName());
        return false;
    }

    private static Class<?> loadGraphics() {
        try {
            return Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor");
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
