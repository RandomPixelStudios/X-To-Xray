package com.xtoxray.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ScreenSwitcher {
    private static final String[] CLIENT_CLASSES = {
        "net.minecraft.client.Minecraft",
        "net.minecraft.client.MinecraftClient"
    };
    private static final String[] SCREEN_CLASSES = {
        "net.minecraft.client.gui.screens.Screen"
    };

    private static Method screenOpener;
    private static Object target;
    private static boolean resolved;

    private ScreenSwitcher() {
    }

    public static void open(Object screen) {
        if (screen == null || !resolve()) {
            return;
        }
        try {
            screenOpener.invoke(target, screen);
        } catch (ReflectiveOperationException e) {
            com.xtoxray.XtoXray.LOGGER.error("Could not open screen", e);
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return screenOpener != null;
        }
        resolved = true;

        Class<?> screenType = firstClass(SCREEN_CLASSES);
        if (screenType == null) {
            return false;
        }

        Class<?> clientType = firstClass(CLIENT_CLASSES);
        if (clientType == null) {
            return false;
        }
        Object client = clientInstance();
        if (client == null) {
            return false;
        }

        Method onGui = methodOn("net.minecraft.client.gui.Gui", "setScreen", screenType);
        if (onGui != null) {
            Object gui = readField(clientType, client, "gui");
            if (gui != null) {
                screenOpener = onGui;
                target = gui;
                return true;
            }
        }

        Method onClient = methodOn(clientType.getName(), "setScreen", screenType);
        if (onClient != null) {
            screenOpener = onClient;
            target = client;
            return true;
        }

        com.xtoxray.XtoXray.LOGGER.error("No setScreen(Screen) found on Gui or the client class");
        return false;
    }

    private static Method methodOn(String className, String name, Class<?> arg) {
        Class<?> owner = load(className);
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, arg);
        } catch (NoSuchMethodException | LinkageError e) {
            return null;
        }
    }

    private static Object readField(Class<?> owner, Object instance, String name) {
        try {
            Field field = owner.getField(name);
            return field.get(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Object clientInstance() {
        for (String name : CLIENT_CLASSES) {
            Class<?> type = load(name);
            if (type == null) {
                continue;
            }
            try {
                return type.getMethod("getInstance").invoke(null);
            } catch (ReflectiveOperationException | LinkageError e) {
                continue;
            }
        }
        return null;
    }

    private static Class<?> firstClass(String[] names) {
        for (String name : names) {
            Class<?> type = load(name);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
