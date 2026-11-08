package com.xtoxray.plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Calls that moved between Minecraft 26.x versions. One jar runs on all of them, so these are looked up at runtime. */
final class GameCompat {
    private GameCompat() {
    }

    static boolean screenOpen() {
        Minecraft mc = Minecraft.getInstance();
        try {
            return read(Minecraft.class, "screen", mc) != null;
        } catch (ReflectiveOperationException old) {
            try {
                return mc.gui.getClass().getMethod("screen").invoke(mc.gui) != null;
            } catch (ReflectiveOperationException e) {
                // If this cannot be answered, treat a menu as open: key reading must fail closed.
                return true;
            }
        }
    }

    private static Object read(Class<?> owner, String field, Object instance) throws ReflectiveOperationException {
        Field f = owner.getField(field);
        return f.get(instance);
    }

    static boolean keyDown(int key) throws ReflectiveOperationException {
        Class<?> input = com.mojang.blaze3d.platform.InputConstants.class;
        try {
            return (boolean) input.getMethod("isKeyDown", int.class).invoke(null, key);
        } catch (NoSuchMethodException old) {
            Minecraft mc = Minecraft.getInstance();
            return (boolean) input.getMethod("isKeyDown", mc.getWindow().getClass(), int.class).invoke(null, mc.getWindow(), key);
        }
    }

    static void title(Component title, Component subtitle) throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        Object target = mc.gui;
        try {
            target = read(mc.gui.getClass(), "hud", mc.gui);
        } catch (NoSuchFieldException old) {
            // Before 26.2 the titles live directly on Gui.
        }
        Method setTitle = target.getClass().getMethod("setTitle", Component.class);
        Method setSubtitle = target.getClass().getMethod("setSubtitle", Component.class);
        setSubtitle.invoke(target, subtitle);
        setTitle.invoke(target, title);
    }
}
