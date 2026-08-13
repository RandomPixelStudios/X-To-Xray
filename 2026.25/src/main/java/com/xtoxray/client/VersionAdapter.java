package com.xtoxray.client;

import com.xtoxray.XtoXray;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;

/**
 * Runtime version detection and cross-version API adapter.
 * Compiled against MC 26.1 but loads on 26.1 – 26.2+.
 * <p>
 * Design principles:
 * - Static initializer detects the MC version string once.
 * - Reflection wrappers for any API that may differ between versions.
 * - Every reflection call is wrapped in try/catch so the mod degrades
 *   gracefully rather than crashing when a method is missing.
 */
public final class VersionAdapter {

    /** Parsed major.minor version */
    public enum MCVersion {
        UNKNOWN,
        V1_21_11,
        V26_1,
        V26_1_1,
        V26_1_2,
        V26_2,
        V26_3;

        public boolean isAtLeast(MCVersion other) {
            return this.ordinal() >= other.ordinal();
        }

        /** Returns true if this is a 1.21.x version (older API). */
        public boolean isLegacy() {
            return this == V1_21_11;
        }
    }

    private static final MCVersion DETECTED_VERSION;
    private static final String DETECTED_VERSION_STRING;

    static {
        MCVersion ver = MCVersion.UNKNOWN;
        String raw = "unknown";
        try {
            // SharedConstants.getCurrentVersion().name() gives us the version string
            var worldVersion = SharedConstants.getCurrentVersion();
            if (worldVersion != null) {
                raw = worldVersion.name();
            }
            ver = parseVersion(raw);
        } catch (Throwable t) {
            XtoXray.LOGGER.warn("XtoXray: Could not detect MC version, assuming latest", t);
            ver = MCVersion.V26_3; // assume latest
        }
        DETECTED_VERSION = ver;
        DETECTED_VERSION_STRING = raw;
        XtoXray.LOGGER.info("XtoXray: Detected MC version {} (enum={})", raw, ver);
    }

    private VersionAdapter() {}

    /** Get the detected MC version enum. */
    public static MCVersion getVersion() {
        return DETECTED_VERSION;
    }

    /** Get the raw version string (e.g. "26.1", "26.1.1"). */
    public static String getVersionString() {
        return DETECTED_VERSION_STRING;
    }

    /** Convenience: is the running version at least the given version? */
    public static boolean isAtLeast(MCVersion version) {
        return DETECTED_VERSION.isAtLeast(version);
    }

    // ---------------------------------------------------------------
    // Version string parser
    // ---------------------------------------------------------------
    private static MCVersion parseVersion(String raw) {
        String lower = raw.toLowerCase().trim();
        // MC 1.21.x (old versioning, "1.21.11", "1.21.4", etc.)
        if (lower.startsWith("1.21")) return MCVersion.V1_21_11;
        // MC 26.x (new versioning)
        if (lower.startsWith("26.1.2")) return MCVersion.V26_1_2;
        if (lower.startsWith("26.1.1")) return MCVersion.V26_1_1;
        if (lower.startsWith("26.1"))   return MCVersion.V26_1;
        if (lower.startsWith("26.2"))   return MCVersion.V26_2;
        if (lower.startsWith("26.3"))   return MCVersion.V26_3;
        // Handle snapshot versions (e.g. "26w33a")
        if (lower.startsWith("26w"))    return MCVersion.V26_3;
        // Also handle "26_1" style underscores
        if (lower.contains("26_1_2") || lower.contains("26.1.2")) return MCVersion.V26_1_2;
        if (lower.contains("26_1_1") || lower.contains("26.1.1")) return MCVersion.V26_1_1;
        if (lower.contains("26_1") && !lower.contains("26_1_1") && !lower.contains("26_1_2")) return MCVersion.V26_1;
        if (lower.contains("26_2") || lower.contains("26.2")) return MCVersion.V26_2;
        if (lower.contains("26_3") || lower.contains("26.3")) return MCVersion.V26_3;
        // Handle "1.21" style underscores
        if (lower.contains("1_21")) return MCVersion.V1_21_11;
        return MCVersion.UNKNOWN;
    }

    // ---------------------------------------------------------------
    // Reflection-based API adapters
    // ---------------------------------------------------------------

    /**
     * Cross-version screen setter.
     * MC 26.1: Minecraft.setScreen(screen) / Minecraft.screen
     * MC 26.2: Minecraft.gui.setScreen(screen) / Minecraft.gui.screen()
     */
    private static Boolean screenOnGui = null; // null = not yet detected

    /** Set the current screen on the Minecraft client, handling version differences. */
    public static void setScreen(net.minecraft.client.Minecraft mc, net.minecraft.client.gui.screens.Screen screen) {
        detectScreenApi(mc);
        try {
            if (screenOnGui) {
                var guiField = net.minecraft.client.Minecraft.class.getField("gui");
                var guiObj = guiField.get(mc);
                guiObj.getClass().getMethod("setScreen", net.minecraft.client.gui.screens.Screen.class).invoke(guiObj, screen);
            } else {
                var setScreenMethod = net.minecraft.client.Minecraft.class.getMethod("setScreen", net.minecraft.client.gui.screens.Screen.class);
                setScreenMethod.invoke(mc, screen);
            }
        } catch (Throwable t) {
            XtoXray.LOGGER.error("XtoXray: Failed to set screen", t);
        }
    }

    /** Detect the screen API variant without side effects. */
    private static void detectScreenApi(net.minecraft.client.Minecraft mc) {
        if (screenOnGui != null) return;
        try {
            var guiField = net.minecraft.client.Minecraft.class.getField("gui");
            var guiObj = guiField.get(mc);
            guiObj.getClass().getMethod("setScreen", net.minecraft.client.gui.screens.Screen.class);
            screenOnGui = true;
        } catch (Throwable t) {
            screenOnGui = false;
        }
    }

    /** Get the current screen from the Minecraft client. */
    public static net.minecraft.client.gui.screens.Screen getScreen(net.minecraft.client.Minecraft mc) {
        detectScreenApi(mc);
        try {
            if (screenOnGui) {
                var guiField = net.minecraft.client.Minecraft.class.getField("gui");
                var guiObj = guiField.get(mc);
                var screenField = guiObj.getClass().getField("screen");
                return (net.minecraft.client.gui.screens.Screen) screenField.get(guiObj);
            } else {
                var screenField = net.minecraft.client.Minecraft.class.getField("screen");
                return (net.minecraft.client.gui.screens.Screen) screenField.get(mc);
            }
        } catch (Throwable t) {
            XtoXray.LOGGER.error("XtoXray: Failed to get screen", t);
            return null;
        }
    }

    /**
     * Register HUD element via reflection.
     * The HudElementRegistry API may have changed method signatures between versions.
     * This tries multiple known signatures and falls back gracefully.
     *
     * @param anchorId  The VanillaHudElements constant identifier to anchor before
     * @param elementId Our mod's element identifier
     * @param renderCallback The rendering functional interface
     */
    public static void registerHudElement(Object anchorId, Object elementId, Object renderCallback) {
        // Try direct call first (works if compiled against same version)
        try {
            Class<?> registry = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
            // Try signature: attachElementBefore(Identifier, Identifier, HudElement.RenderContext -> void)
            Method m = findMethod(registry, "attachElementBefore",
                    findClass("net.minecraft.resources.Identifier"),
                    findClass("net.minecraft.resources.Identifier"),
                    findClass("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement"));
            if (m != null) {
                m.invoke(null, anchorId, elementId, renderCallback);
                XtoXray.LOGGER.debug("HudElement registered via direct call");
                return;
            }
        } catch (Throwable t) {
            XtoXray.LOGGER.debug("HudElement direct registration failed, trying fallback", t);
        }

        // Fallback: try attachElementBefore with any 3-parameter signature
        try {
            Class<?> registry = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
            for (Method m : registry.getMethods()) {
                if (m.getName().equals("attachElementBefore") && m.getParameterCount() == 3) {
                    m.setAccessible(true);
                    m.invoke(null, anchorId, elementId, renderCallback);
                    XtoXray.LOGGER.debug("HudElement registered via fallback reflection");
                    return;
                }
            }
        } catch (Throwable t) {
            XtoXray.LOGGER.debug("HudElement fallback registration also failed", t);
        }

        XtoXray.LOGGER.warn("XtoXray: Could not register HUD element for container view – feature disabled on MC {}", DETECTED_VERSION_STRING);
    }

    /**
     * Get VanillaHudElements.MISC_OVERLAYS via reflection.
     */
    public static Object getMiscOverlaysConstant() {
        try {
            Class<?> vh = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements");
            var field = vh.getField("MISC_OVERLAYS");
            return field.get(null);
        } catch (Throwable t) {
            XtoXray.LOGGER.debug("Could not get VanillaHudElements.MISC_OVERLAYS", t);
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Reflection helpers
    // ---------------------------------------------------------------

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

}
