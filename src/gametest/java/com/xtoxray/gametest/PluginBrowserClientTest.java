package com.xtoxray.gametest;

import com.xtoxray.client.ScreenSwitcher;
import com.xtoxray.client.gui.XrayConfigScreen;
import com.xtoxray.plugin.ExternalPlugin;
import com.xtoxray.plugin.PluginDefinition;
import com.xtoxray.plugin.PluginManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/** Opens the real menu in a real client, installs a plugin from the signed list and takes screenshots. */
public class PluginBrowserClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        System.setProperty("xtoxray.noDownloadCount", "true");
        context.runOnClient(client -> {
            client.options.guiScale().set(1);
            client.resizeGui();
            ScreenSwitcher.open(new net.minecraft.client.gui.screens.TitleScreen());
        });
        for (int i = 0; i < 300 && !context.computeOnClient(client -> com.xtoxray.client.news.NewsManager.getCurrentEntry() != null
            && com.xtoxray.client.news.NewsManager.getCurrentEntry().getImage().contains("news_remote")); i++) {
            context.waitTicks(2);
        }
        context.takeScreenshot("0-news-title-screen");
        String news = context.computeOnClient(client -> com.xtoxray.client.news.NewsOverlay.debugState()
            + " image=" + com.xtoxray.client.news.NewsManager.getCurrentEntry().getImage());
        System.out.println("XTOXRAY-TEST news " + news);
        if (!news.startsWith("visible=true") || !news.contains("news_remote")) throw new AssertionError("News not shown: " + news);
        context.runOnClient(client -> com.xtoxray.client.news.NewsConsent.ask());
        context.waitTicks(10);
        context.takeScreenshot("0-news-consent");
        context.runOnClient(client -> {
            XrayConfigScreen screen = new XrayConfigScreen();
            ScreenSwitcher.open(screen);
            screen.openPluginsTab(false);
        });
        context.waitTicks(20);
        context.takeScreenshot("1-plugin-browser");

        context.runOnClient(client -> {
            PluginManager manager = PluginManager.getInstance();
            PluginDefinition veinminer = manager.getCatalog().stream().filter(p -> p.id().equals("veinminer")).findFirst().orElseThrow();
            String error = manager.install(veinminer);
            if (error != null) throw new AssertionError(error);
            ExternalPlugin installed = manager.installedById("veinminer");
            if (installed == null || installed.getRuntime() == null) throw new AssertionError("Plugin is not running: " + (installed == null ? "missing" : installed.getStatus()));
            XrayConfigScreen screen = new XrayConfigScreen();
            ScreenSwitcher.open(screen);
            screen.openPluginsTab(true);
        });
        context.waitTicks(20);
        context.takeScreenshot("2-installed");

        context.runOnClient(client -> {
            XrayConfigScreen screen = new XrayConfigScreen();
            ScreenSwitcher.open(screen);
            screen.openTab(2);
        });
        context.waitTicks(20);
        context.takeScreenshot("3-veinminer-settings-main-menu");

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(100);
            BlockPos base = context.computeOnClient(client -> client.player.blockPosition()).offset(2, 0, 0);
            BlockPos second = base.above();
            BlockPos third = base.above(2);
            BlockPos separate = base.offset(3, 0, 0);
            for (BlockPos pos : new BlockPos[]{base, second, third, separate}) {
                world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:diamond_ore");
            }
            context.waitTicks(10);
            context.runOnClient(client -> client.gameMode.destroyBlock(base));
            context.waitTicks(10);
            String result = world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                return level.getBlockState(second).isAir() + " " + level.getBlockState(third).isAir() + " " + level.getBlockState(separate).isAir();
            });
            if (!result.equals("true true false")) throw new AssertionError("Veinminer result (second third separate air): " + result);
            System.out.println("XTOXRAY-TEST veinminer ok in real world");
            testApi(context, world);

            context.runOnClient(client -> {
                XrayConfigScreen screen = new XrayConfigScreen();
                ScreenSwitcher.open(screen);
                screen.openTab(2);
            });
            context.waitTicks(20);
            context.takeScreenshot("4-veinminer-settings-in-world");

            context.runOnClient(client -> {
                PluginManager manager = PluginManager.getInstance();
                manager.install(manager.getCatalog().stream().filter(p -> p.id().equals("hitboxes")).findFirst().orElseThrow());
                XrayConfigScreen screen = new XrayConfigScreen();
                ScreenSwitcher.open(screen);
                screen.openPluginsTab(false);
            });
            context.waitTicks(20);
            context.takeScreenshot("5-browser-after-install");
            context.runOnClient(client -> {
                ExternalPlugin hitboxes = PluginManager.getInstance().installedById("hitboxes");
                if (hitboxes.getStatus() != ExternalPlugin.Status.ENABLED) throw new AssertionError("Hitboxes stopped: " + hitboxes.getStatus());
                ScreenSwitcher.open(null);
            });
        }
    }

    /** Mining Stats and every API group in a real world, on whichever Minecraft version this runs. */
    private static void testApi(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> {
            try {
                PluginManager manager = PluginManager.getInstance();
                String stats = java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("xtoxray.testPlugins"), "mining-stats.lua"));
                String api = """
                    --[[xtoxray
                    {"format":1,"id":"api-check","name":"API Check","version":"1","permissions":["hud","player.read","player.control","input","sound","options","chat","chat.read","world.read","entities.read","render","xray"]}
                    ]]
                    local ok = {}
                    local function check(name, f, ...)
                      local good, value = pcall(f, ...)
                      if not good then xtx.log("XTOXRAY-API-FAIL " .. name .. " " .. tostring(value)) end
                    end
                    xtx.hud.hide("hotbar")
                    xtx.command("apicheck", function()
                      check("info", xtx.player.info)
                      check("inventory", xtx.player.inventory)
                      check("set_look", xtx.player.set_look, 10, 5)
                      check("select_slot", xtx.player.select_slot, 2)
                      check("set_control", xtx.player.set_control, "jump", false)
                      check("is_down", xtx.input.is_down, "key.keyboard.h")
                      check("sound", xtx.sound.play, "minecraft:ui.button.click")
                      check("opt", xtx.options.get, "fov")
                      check("opt2", xtx.options.set, "fov", 80)
                      check("opt3", xtx.options.get, "gamma")
                      check("opt4", xtx.options.get, "render_distance")
                      check("opt5", xtx.options.get, "view_bobbing")
                      check("actionbar", xtx.actionbar, "action")
                      check("title", xtx.title, "", "")
                      check("world", xtx.world.info)
                      check("light", xtx.world.light, 0, 64, 0)
                      check("biome", xtx.world.biome, 0, 64, 0)
                      check("look", xtx.world.looking_at, 5)
                      check("ents", xtx.world.entity_list, 32)
                      check("nv", xtx.xray.night_vision)
                      check("rd", xtx.xray.render_distance)
                      check("active", xtx.xray.set_active, false)
                      xtx.log("XTOXRAY-API-DONE ")
                    end)
                    xtx.on("chat_message", function(text) if text:find("hidden", 1, true) then xtx.log("XTOXRAY-API-CHAT ok"); return false end end)
                    xtx.on("render", function() xtx.draw.line(0,0,0,1,1,1); xtx.draw.text("x",0,0,0) end)
                    xtx.on("hud", function()
                      xtx.hud.rect(40, 40, 60, 60)
                      xtx.hud.text("HUD API OK", 40, 64, 0xFF55FF55)
                      xtx.hud.item("minecraft:diamond", 64, 40)
                      xtx.hud.size(); xtx.hud.text_width("a")
                    end)
                    """.stripIndent();
                for (String source : new String[]{stats, api}) {
                    String error = manager.install(PluginDefinition.parse(source));
                    if (error != null) throw new AssertionError(error);
                }
                for (String id : new String[]{"mining-stats", "api-check"}) {
                    ExternalPlugin plugin = manager.installedById(id);
                    if (plugin == null || plugin.getRuntime() == null) throw new AssertionError(id + " is not running");
                }
                manager.runCommand("apicheck");
                client.player.connection.sendCommand("xtx stats");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        BlockPos ore = context.computeOnClient(client -> client.player.blockPosition()).offset(-2, 0, 0);
        world.getServer().runCommand("setblock " + ore.getX() + " " + ore.getY() + " " + ore.getZ() + " minecraft:iron_ore");
        world.getServer().runCommand("say hidden message");
        context.waitTicks(10);
        context.runOnClient(client -> client.gameMode.destroyBlock(ore));
        context.waitTicks(20);
        context.takeScreenshot("9-hud-api");
        boolean stopped = context.computeOnClient(client -> PluginManager.getInstance().installedById("api-check").getRuntime() == null);
        if (stopped) throw new AssertionError("api-check crashed");
        System.out.println("XTOXRAY-TEST api ok");
        testXrayRendering(context, world);
    }

    /** Xray has to hide the ground in whichever renderer is active; compare the two screenshots. */
    private static void testXrayRendering(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos ore = context.computeOnClient(client -> client.player.blockPosition()).offset(0, -1, 3);
        world.getServer().runCommand("setblock " + ore.getX() + " " + ore.getY() + " " + ore.getZ() + " minecraft:diamond_ore");
        context.runOnClient(client -> {
            client.player.setYRot(0);
            client.player.setXRot(35);
        });
        context.waitTicks(60);
        context.takeScreenshot("10-xray-off");
        context.runOnClient(com.xtoxray.client.XrayToggleHandler::toggleXray);
        context.waitTicks(100);
        context.takeScreenshot("11-xray-on");
        context.runOnClient(com.xtoxray.client.XrayToggleHandler::toggleXray);
        context.waitTicks(20);
    }
}
