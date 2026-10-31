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
}
