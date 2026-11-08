package com.xtoxray.client;

import com.xtoxray.plugin.PluginManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.luaj.vm2.LuaValue;

/** Connects game events to plugins. HudElement changes between 26.x versions, so it is implemented through a proxy that fits all of them. */
final class PluginEvents {
    private static final Map<String, Identifier> PARTS = Map.ofEntries(
        Map.entry("hotbar", VanillaHudElements.HOTBAR), Map.entry("health", VanillaHudElements.HEALTH_BAR),
        Map.entry("armor", VanillaHudElements.ARMOR_BAR), Map.entry("food", VanillaHudElements.FOOD_BAR),
        Map.entry("air", VanillaHudElements.AIR_BAR), Map.entry("mount_health", VanillaHudElements.MOUNT_HEALTH),
        Map.entry("experience_bar", VanillaHudElements.INFO_BAR), Map.entry("experience_level", VanillaHudElements.EXPERIENCE_LEVEL),
        Map.entry("crosshair", VanillaHudElements.CROSSHAIR), Map.entry("effects", VanillaHudElements.MOB_EFFECTS),
        Map.entry("boss_bar", VanillaHudElements.BOSS_BAR), Map.entry("scoreboard", VanillaHudElements.SCOREBOARD),
        Map.entry("chat", VanillaHudElements.CHAT), Map.entry("player_list", VanillaHudElements.PLAYER_LIST),
        Map.entry("subtitles", VanillaHudElements.SUBTITLES), Map.entry("title", VanillaHudElements.TITLE_AND_SUBTITLE),
        Map.entry("overlay_message", VanillaHudElements.OVERLAY_MESSAGE), Map.entry("held_item_tooltip", VanillaHudElements.HELD_ITEM_TOOLTIP));

    private PluginEvents() {
    }

    static void register() {
        PluginManager manager = PluginManager.getInstance();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("xtoxray", "plugins"), proxy((self, method, args) -> {
            if (method.getName().equals("extractRenderState")) {
                manager.hudHost().drawHud((GuiGraphicsExtractor) args[0]);
                return null;
            }
            if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
            return objectMethod(self, method.getName(), args);
        }));
        PARTS.forEach((part, id) -> HudElementRegistry.replaceElement(id, original -> proxy((self, method, args) -> {
            if (method.getName().equals("extractRenderState") && manager.isHudPartHidden(part)) return null;
            try {
                return method.invoke(original, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        })));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> manager.fire("join"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> manager.fire("leave"));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            manager.fire("shutdown");
            manager.saveAllSettings();
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
            overlay || manager.fire("chat_message", LuaValue.valueOf(message.getString()), LuaValue.valueOf("game")));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) ->
            manager.fire("chat_message", LuaValue.valueOf(message.getString()), LuaValue.valueOf("chat")));
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (!command.equals("xtx") && !command.startsWith("xtx ")) return true;
            manager.runCommand(command.substring(3));
            return false;
        });
    }

    private static HudElement proxy(InvocationHandler handler) {
        return (HudElement) Proxy.newProxyInstance(HudElement.class.getClassLoader(), new Class<?>[]{HudElement.class}, handler);
    }

    private static Object objectMethod(Object self, String name, Object[] args) {
        return switch (name) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            default -> "XtoXrayPluginHud";
        };
    }
}
