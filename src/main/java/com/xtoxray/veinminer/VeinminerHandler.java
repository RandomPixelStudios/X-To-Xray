package com.xtoxray.veinminer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class VeinminerHandler {
    private static final VeinminerHandler INSTANCE = new VeinminerHandler();
    private boolean mining = false;

    private VeinminerHandler() {
    }

    public static VeinminerHandler getInstance() {
        return INSTANCE;
    }

    public void onBlockBroken(BlockPos pos, Block sourceBlock) {
        if (mining) return;
        VeinminerState state = VeinminerState.getInstance();
        if (!state.isEnabled()) return;
        if (!com.xtoxray.plugin.PluginState.getInstance().isInstalled("Veinminer")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (!state.isWhitelisted(sourceBlock)) return;
        List<BlockPos> vein = findVein(mc, pos, sourceBlock, state.getMaxBlocks());
        if (vein.isEmpty()) return;
        mining = true;
        try {
            int mode = state.getSilkTouchMode();
            ItemStack tool = mc.player.getMainHandItem();
            var silkTouchHolder = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
            boolean hadSilkTouch = false;
            if (mode == VeinminerState.SILK_TOUCH_ALWAYS) {
                hadSilkTouch = EnchantmentHelper.getItemEnchantmentLevel(silkTouchHolder, tool) > 0;
                if (!hadSilkTouch) {
                    EnchantmentHelper.updateEnchantments(tool, mutable -> mutable.set(silkTouchHolder, 1));
                }
            } else if (mode == VeinminerState.SILK_TOUCH_NEVER) {
                hadSilkTouch = EnchantmentHelper.getItemEnchantmentLevel(silkTouchHolder, tool) > 0;
                if (hadSilkTouch) {
                    EnchantmentHelper.updateEnchantments(tool, mutable -> mutable.set(silkTouchHolder, 0));
                }
            }
            for (BlockPos veinPos : vein) {
                if (mineBlockServer(mc, veinPos)) continue;
                mc.player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    veinPos,
                    Direction.DOWN
                ));
            }
            if (mode == VeinminerState.SILK_TOUCH_ALWAYS && !hadSilkTouch) {
                EnchantmentHelper.updateEnchantments(tool, mutable -> mutable.set(silkTouchHolder, 0));
            } else if (mode == VeinminerState.SILK_TOUCH_NEVER && hadSilkTouch) {
                EnchantmentHelper.updateEnchantments(tool, mutable -> mutable.set(silkTouchHolder, 1));
            }
            int cost = state.getDurabilityCost();
            if (cost > 0 && !tool.isEmpty()) {
                int extra = cost * (vein.size() - 1);
                if (extra > 0) {
                    tool.setDamageValue(tool.getDamageValue() + extra);
                }
            }
        } finally {
            mining = false;
        }
    }

    private boolean mineBlockServer(Minecraft mc, BlockPos pos) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null || mc.player == null) return false;
        ServerLevel serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) return false;
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
        mc.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        server.execute(() -> serverLevel.destroyBlock(pos, true, serverPlayer, 3));
        return true;
    }

    private List<BlockPos> findVein(Minecraft mc, BlockPos start, Block target, int max) {
        List<BlockPos> result = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(start);
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = start.relative(dir);
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                BlockState state = mc.level.getBlockState(neighbor);
                if (state.getBlock() == target) {
                    queue.add(neighbor);
                    result.add(neighbor);
                }
            }
        }
        while (!queue.isEmpty() && result.size() < max) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    BlockState state = mc.level.getBlockState(neighbor);
                    if (state.getBlock() == target) {
                        queue.add(neighbor);
                        result.add(neighbor);
                    }
                }
            }
        }
        return result;
    }
}
