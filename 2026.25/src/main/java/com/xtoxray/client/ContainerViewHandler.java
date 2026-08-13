/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
 *  net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.resources.Identifier
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.Container
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult
 *  net.minecraft.world.phys.HitResult$Type
 */
package com.xtoxray.client;

import com.xtoxray.XrayState;
import com.xtoxray.XtoXray;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Container View handler — renders overlay showing chest/barrel contents.
 * Uses reflection for cross-version compatibility (Identifier, GuiGraphicsExtractor, DeltaTracker).
 */
public class ContainerViewHandler {
    private static volatile ContainerViewData currentView = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ContainerViewHandler.tick(client));
    }

    /**
     * Cross-version render method. Receives graphics/delta as Object to avoid
     * class-loading GuiGraphicsExtractor or DeltaTracker at class-load time.
     * Uses reflection to invoke the actual graphics methods.
     */
    public static void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        renderHud(graphics, delta);
    }
    public static void renderHud(Object graphics, Object delta) {
        if (currentView == null) return;
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int cols = 9;
        int slotSize = 18;
        int gap = 2;
        int rows = Math.max(1, (currentView.items.size() + cols - 1) / cols);
        int panelW = cols * (slotSize + gap) - gap + 20;
        int panelH = rows * (slotSize + gap) - gap + 50;
        int panelX = (screenWidth - panelW) / 2;
        int panelY = (screenHeight - panelH) / 2;
        try {
            // graphics.fill(x1, y1, x2, y2, color)
            var fillMethod = graphics.getClass().getMethod("fill", int.class, int.class, int.class, int.class, int.class);
            fillMethod.invoke(graphics, panelX, panelY, panelX + panelW, panelY + panelH, -1072689136);
            fillMethod.invoke(graphics, panelX, panelY, panelX + panelW, panelY + 1, -11751600);
            fillMethod.invoke(graphics, panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, -11751600);
            fillMethod.invoke(graphics, panelX, panelY, panelX + 1, panelY + panelH, -11751600);
            fillMethod.invoke(graphics, panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, -11751600);
        } catch (Throwable ignored) {}

        // centeredText(font, text, x, y, color)
        try {
            var ctMethod = graphics.getClass().getMethod("centeredText",
                    net.minecraft.client.gui.Font.class, Component.class, int.class, int.class, int.class);
            ctMethod.invoke(graphics, mc.font, currentView.name, screenWidth / 2, panelY + 8, -1);
        } catch (Throwable ignored) {
            // Fallback: try drawCenteredString or text
            try {
                var textMethod = graphics.getClass().getMethod("text",
                        net.minecraft.client.gui.Font.class, Component.class, int.class, int.class, int.class);
                int textW = mc.font.width((net.minecraft.network.chat.FormattedText)currentView.name);
                textMethod.invoke(graphics, mc.font, currentView.name, (screenWidth - textW) / 2, panelY + 8, -1);
            } catch (Throwable ignored2) {}
        }

        int startX = panelX + 10;
        int startY = panelY + 28;
        for (int i = 0; i < currentView.items.size(); ++i) {
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (slotSize + gap);
            int y = startY + row * (slotSize + gap);
            try {
                var fillMethod = graphics.getClass().getMethod("fill", int.class, int.class, int.class, int.class, int.class);
                fillMethod.invoke(graphics, x, y, x + slotSize, y + slotSize, -13421773);
                fillMethod.invoke(graphics, x, y, x + slotSize, y + 1, -11184811);
                fillMethod.invoke(graphics, x, y + slotSize - 1, x + slotSize, y + slotSize, -11184811);
                fillMethod.invoke(graphics, x, y, x + 1, y + slotSize, -11184811);
                fillMethod.invoke(graphics, x + slotSize - 1, y, x + slotSize, y + slotSize, -11184811);
            } catch (Throwable ignored) {}

            ItemStack stack = currentView.items.get(i);
            if (stack.isEmpty()) continue;
            // fakeItem / renderItem
            try {
                var fiMethod = graphics.getClass().getMethod("fakeItem", ItemStack.class, int.class, int.class);
                fiMethod.invoke(graphics, stack, x + 1, y + 1);
            } catch (Throwable ignored) {
                try {
                    var riMethod = graphics.getClass().getMethod("renderItem", ItemStack.class, int.class, int.class);
                    riMethod.invoke(graphics, stack, x + 1, y + 1);
                } catch (Throwable ignored2) {}
            }
            // itemDecorations
            try {
                var idMethod = graphics.getClass().getMethod("itemDecorations",
                        net.minecraft.client.gui.Font.class, ItemStack.class, int.class, int.class);
                idMethod.invoke(graphics, mc.font, stack, x + 1, y + 1);
            } catch (Throwable ignored) {}
        }
    }

    private static void tick(Minecraft client) {
        if (!XrayState.getInstance().isActive() || !XrayState.getInstance().isShowContainerView() || client.player == null || client.level == null) {
            currentView = null;
            return;
        }
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            currentView = null;
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        Block block = client.level.getBlockState(pos).getBlock();
        if (!isContainerBlock(block)) {
            currentView = null;
            return;
        }
        if (currentView != null && currentView.pos.equals(pos)) {
            return;
        }
        if (client.hasSingleplayerServer()) {
            BlockPos immPos = pos.immutable();
            ResourceKey<net.minecraft.world.level.Level> dimension = client.level.dimension();
            MutableComponent name = block.getName();
            client.getSingleplayerServer().executeIfPossible(() -> {
                ServerLevel serverLevel = client.getSingleplayerServer().getLevel(dimension);
                if (serverLevel != null) {
                    BlockEntity be = serverLevel.getBlockEntity(immPos);
                    if (be instanceof Container container) {
                        int size = container.getContainerSize();
                        ArrayList<ItemStack> items = new ArrayList<>(size);
                        for (int i = 0; i < size; ++i) {
                            items.add(container.getItem(i).copy());
                        }
                        XtoXray.LOGGER.info("ContainerView: server task got container {} size={}", immPos, size);
                        currentView = new ContainerViewData(immPos, items, (Component) name);
                    } else {
                        XtoXray.LOGGER.info("ContainerView: server BE at {} is not container: {}", immPos, be);
                    }
                }
            });
        } else {
            BlockEntity be = client.level.getBlockEntity(pos);
            if (be instanceof Container container) {
                int size = container.getContainerSize();
                ArrayList<ItemStack> items = new ArrayList<>(size);
                for (int i = 0; i < size; ++i) {
                    items.add(container.getItem(i).copy());
                }
                XtoXray.LOGGER.info("ContainerView: client container at {} size={}", pos, size);
                currentView = new ContainerViewData(pos, items, block.getName());
            } else {
                currentView = null;
            }
        }
    }

    private static boolean isContainerBlock(Block block) {
        return XrayState.getInstance().isContainerWhitelisted(block);
    }

    private record ContainerViewData(BlockPos pos, List<ItemStack> items, Component name) {}
}

