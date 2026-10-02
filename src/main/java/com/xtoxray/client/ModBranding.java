package com.xtoxray.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class ModBranding {
    private static String cached;

    private ModBranding() {
    }

    public static void render(Screen screen, GuiGraphicsExtractor graphics) {
        Font font = Minecraft.getInstance().font;
        String name = "X To Xray ";
        String version = version();
        int x = 2;
        int y = screen.height - 20;
        graphics.text(font, name, x, y, 0xFFFF55, true);
        graphics.text(font, version, x + font.width(name), y, 0x808080, true);
    }

    private static String version() {
        if (cached == null) {
            cached = FabricLoader.getInstance().getModContainer("xtoxray")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("dev");
        }
        return cached;
    }
}
