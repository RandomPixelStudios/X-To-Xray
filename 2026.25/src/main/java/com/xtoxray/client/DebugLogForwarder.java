package com.xtoxray.client;

import com.xtoxray.XtoXray;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

public class DebugLogForwarder {
    private static volatile boolean active;
    private static volatile boolean appenderRegistered;
    private static AbstractAppender appender;

    private static int xrayUseCount;
    private static int xrayErrorCount;
    private static int modErrorCount;

    public static void init() {
        if (appenderRegistered) return;
        appenderRegistered = true;
        try {
            appender = new AbstractAppender("XtoXrayDebugLog", null, null, false) {
                @Override
                public void append(LogEvent event) {
                    String level = event.getLevel() != null ? event.getLevel().toString() : "?";
                    if (level.equalsIgnoreCase("ERROR") || level.equalsIgnoreCase("WARN")) {
                        if (event.getLoggerName() != null && event.getLoggerName().contains("xtoxray")) {
                            incrementModError();
                        }
                    }
                }
            };
            appender.start();
            org.apache.logging.log4j.core.LoggerContext ctx = (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
            ctx.getRootLogger().addAppender(appender);
        } catch (Throwable t) {
            appenderRegistered = false;
        }
    }

    public static void install() {
        active = true;
    }

    public static void uninstall() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Register the debug HUD through the Fabric HudElementRegistry using a dynamic Proxy.
     * This avoids any compile-time dependency on the version-specific graphics class
     * (GuiGraphics on 26.1, GuiGraphicsExtractor on 26.2) and works on all versions.
     */
    public static void registerHud() {
        try {
            Class<?> registryClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
            Class<?> elementClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement");
            Class<?> vanillaClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements");
            Class<?> anchorClass = vanillaClass.getField("MISC_OVERLAYS").get(null).getClass();
            Object anchor = vanillaClass.getField("MISC_OVERLAYS").get(null);

            Object id = null;
            Class<?> idClass = null;
            try {
                idClass = Class.forName("net.minecraft.resources.Identifier");
            } catch (Throwable t1) {
                try {
                    idClass = Class.forName("net.minecraft.resources.ResourceLocation");
                } catch (Throwable t2) {
                    idClass = Class.forName("net.minecraft.util.Identifier");
                }
            }
            id = idClass.getConstructor(String.class, String.class).newInstance("xtoxray", "debug_hud");

            Object proxy = Proxy.newProxyInstance(
                    DebugLogForwarder.class.getClassLoader(),
                    new Class<?>[]{elementClass},
                    (InvocationHandler) (p, method, args) -> {
                        if (method.getName().equals("render") && args != null && args.length >= 2) {
                            renderHud((Object) args[0]);
                        }
                        return null;
                    });

            try {
                Method attach = registryClass.getMethod("attachElementBefore", idClass, idClass, elementClass);
                attach.invoke(null, anchor, id, proxy);
                XtoXray.LOGGER.info("XtoXray: Debug HUD registered via HudElementRegistry.attachElementBefore");
            } catch (NoSuchMethodException e) {
                Method addLast = registryClass.getMethod("addLast", idClass, elementClass);
                addLast.invoke(null, id, proxy);
                XtoXray.LOGGER.info("XtoXray: Debug HUD registered via HudElementRegistry.addLast");
            }
        } catch (Throwable t) {
            XtoXray.LOGGER.warn("XtoXray: Could not register debug HUD element: {}", t.getMessage());
        }
    }

    public static void incrementXrayUse() {
        xrayUseCount++;
    }

    public static void incrementXrayError() {
        xrayErrorCount++;
    }

    public static void incrementModError() {
        modErrorCount++;
    }

    public static void sendStatsToChat() {
        if (!active) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            Component msg = Component.literal("\u00a7a[XtoXray Debug] \u00a7fXray: Used \u00a7e" + xrayUseCount
                    + "\u00a7f Times | Xray Errors: \u00a7e" + xrayErrorCount
                    + "\u00a7f | Mod Errors: \u00a7e" + modErrorCount);
            mc.player.sendSystemMessage(msg);
        } catch (Throwable ignored) {
        }
    }

    public static void renderHud(Object graphics) {
        if (!active) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int pad = 6;
            int lineH = 11;
            String line1 = "Xray: Used " + xrayUseCount + " Times";
            String line2 = "Xray Errors: " + xrayErrorCount;
            String line3 = "Mod Errors: " + modErrorCount;
            int w1 = font.width(line1);
            int w2 = font.width(line2);
            int w3 = font.width(line3);
            int boxW = Math.max(w1, Math.max(w2, w3)) + pad * 2;
            int boxH = lineH * 3 + pad * 2;
            int boxX = screenWidth - boxW - 10;
            int boxY = 10;
            var fillMethod = graphics.getClass().getMethod("fill", int.class, int.class, int.class, int.class, int.class);
            fillMethod.invoke(graphics, boxX, boxY, boxX + boxW, boxY + boxH, -1072689136);
            fillMethod.invoke(graphics, boxX, boxY, boxX + boxW, boxY + 1, -11751600);
            fillMethod.invoke(graphics, boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, -11751600);
            fillMethod.invoke(graphics, boxX, boxY, boxX + 1, boxY + boxH, -11751600);
            fillMethod.invoke(graphics, boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, -11751600);
            var textMethod = graphics.getClass().getMethod("text",
                    Font.class, Component.class, int.class, int.class, int.class);
            textMethod.invoke(graphics, font, Component.literal(line1), boxX + pad, boxY + pad, -1);
            textMethod.invoke(graphics, font, Component.literal(line2), boxX + pad, boxY + pad + lineH, -10197915);
            textMethod.invoke(graphics, font, Component.literal(line3), boxX + pad, boxY + pad + lineH * 2, -10197915);
        } catch (Throwable ignored) {
            // 26.1 / 1.21.x GuiGraphics: drawString(Font, Component, x, y, color)
            try {
                Minecraft mc2 = Minecraft.getInstance();
                Font font2 = mc2.font;
                int screenWidth2 = mc2.getWindow().getGuiScaledWidth();
                int pad2 = 6;
                int lineH2 = 11;
                String line1b = "Xray: Used " + xrayUseCount + " Times";
                String line2b = "Xray Errors: " + xrayErrorCount;
                String line3b = "Mod Errors: " + modErrorCount;
                int w1b = font2.width(line1b);
                int w2b = font2.width(line2b);
                int w3b = font2.width(line3b);
                int boxW2 = Math.max(w1b, Math.max(w2b, w3b)) + pad2 * 2;
                int boxH2 = lineH2 * 3 + pad2 * 2;
                int boxX2 = screenWidth2 - boxW2 - 10;
                int boxY2 = 10;
                var drawMethod = graphics.getClass().getMethod("drawString",
                        Font.class, Component.class, int.class, int.class, int.class);
                drawMethod.invoke(graphics, font2, Component.literal(line1b), boxX2 + pad2, boxY2 + pad2, -1);
                drawMethod.invoke(graphics, font2, Component.literal(line2b), boxX2 + pad2, boxY2 + pad2 + lineH2, -10197915);
                drawMethod.invoke(graphics, font2, Component.literal(line3b), boxX2 + pad2, boxY2 + pad2 + lineH2 * 2, -10197915);
            } catch (Throwable ignored2) {
            }
        }
    }
}
