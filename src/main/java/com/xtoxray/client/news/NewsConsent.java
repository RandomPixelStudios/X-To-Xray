package com.xtoxray.client.news;

import com.xtoxray.XtoXray;
import com.xtoxray.client.ScreenSwitcher;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Asks once on the first start whether news should be shown. Only after a yes is anything downloaded. */
public final class NewsConsent {
    private static Boolean enabled;
    private static boolean asked;

    private NewsConsent() {
    }

    public static void register() {
        enabled = read();
        if (Boolean.TRUE.equals(enabled)) {
            NewsDownloader.start();
        }
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (enabled == null && !asked && screen instanceof TitleScreen) {
                asked = true;
                client.execute(NewsConsent::ask);
            }
        });
    }

    public static boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public static void ask() {
        ScreenSwitcher.open(new ConfirmScreen(NewsConsent::answer,
            Component.literal("X To Xray News"),
            Component.literal("Moechtest du im Hauptmenue News von X To Xray sehen? "
                + "Die Bilder werden dafuer von GitHub heruntergeladen."),
            Component.literal("Ja, News zeigen"),
            Component.literal("Nein danke")));
    }

    private static void answer(boolean yes) {
        enabled = yes;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), "{\"enabled\": " + yes + "}");
        } catch (Exception e) {
            XtoXray.LOGGER.warn("Konnte News-Einstellung nicht speichern", e);
        }
        if (yes) {
            NewsDownloader.start();
        }
        ScreenSwitcher.open(new TitleScreen());
    }

    private static Boolean read() {
        String forced = System.getProperty("xtoxray.news");
        if (forced != null) {
            return Boolean.parseBoolean(forced);
        }
        try {
            if (Files.exists(file())) {
                var obj = com.google.gson.JsonParser.parseString(Files.readString(file())).getAsJsonObject();
                if (obj.has("enabled")) {
                    return obj.get("enabled").getAsBoolean();
                }
            }
        } catch (Exception e) {
            XtoXray.LOGGER.warn("News-Einstellung ungueltig, frage erneut", e);
        }
        return null;
    }

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("xtoxray-news-settings.json");
    }
}
