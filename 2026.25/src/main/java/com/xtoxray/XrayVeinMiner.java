/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.xtoxray;

import com.xtoxray.XrayState;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class XrayVeinMiner {
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (!(world instanceof ServerLevel)) {
                return;
            }
            ServerLevel serverLevel = (ServerLevel)world;
            if (!XrayState.getInstance().isVeinMiner()) {
                return;
            }
            if (!XrayState.getInstance().isVeinMinerWhitelisted(state.getBlock())) {
                return;
            }
            XrayVeinMiner.breakVein(serverLevel, (ServerPlayer)player, pos, state.getBlock());
        });
    }

    private static void breakVein(ServerLevel world, ServerPlayer player, BlockPos startPos, Block block) {
        int maxBlocks = 100;
        HashSet<BlockPos> visited = new HashSet<BlockPos>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<BlockPos>();
        queue.add(startPos);
        visited.add(startPos);
        int broken = 0;
        while (!queue.isEmpty() && broken < maxBlocks) {
            BlockPos current = (BlockPos)queue.poll();
            ++broken;
            for (BlockPos neighbor : XrayVeinMiner.getNeighbors(current)) {
                if (visited.contains(neighbor)) continue;
                visited.add(neighbor);
                BlockState neighborState = world.getBlockState(neighbor);
                if (!neighborState.is(block)) continue;
                queue.add(neighbor);
            }
        }
        XrayState state = XrayState.getInstance();
        int durabilityCost = state.getVeinMinerDurability();
        boolean fortune = state.isVeinMinerFortune();
        boolean silkTouch = state.isVeinMinerSilkTouch();
        for (BlockPos pos : visited) {
            ItemStack held;
            if (pos.equals((Object)startPos)) continue;
            if (broken > maxBlocks) break;
            BlockState blockState = world.getBlockState(pos);
            if (!blockState.is(block)) continue;
            XrayVeinMiner.breakSingle(world, (Entity)player, pos, blockState, fortune, silkTouch);
            if (durabilityCost > 0 && !(held = player.getMainHandItem()).isEmpty() && held.isDamageableItem()) {
                held.hurtAndBreak(durabilityCost, (LivingEntity)player, EquipmentSlot.MAINHAND);
            }
            ++broken;
        }
    }

    private static void breakSingle(ServerLevel world, Entity player, BlockPos pos, BlockState blockState, boolean fortune, boolean silkTouch) {
        try {
            ItemStack tool = player instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
            if (silkTouch && blockState.getBlock() != Blocks.SPAWNER && blockState.getBlock().asItem() != Items.AIR) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                Block.popResource(world, pos, new ItemStack(blockState.getBlock()));
                return;
            }
            if (fortune) {
                ItemStack lootTool = tool;
                Holder<Enchantment> fortuneHolder = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
                if (tool.isEmpty() || tool.getEnchantments().getLevel(fortuneHolder) <= 0) {
                    lootTool = tool.copy();
                    lootTool.enchant(fortuneHolder, 3);
                }
                List<ItemStack> drops = Block.getDrops(blockState, world, pos, (BlockEntity)null, player, lootTool);
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                for (ItemStack drop : drops) {
                    Block.popResource(world, pos, drop);
                }
                return;
            }
            world.destroyBlock(pos, true, (Entity)player);
        } catch (Throwable t) {
            world.destroyBlock(pos, true, (Entity)player);
        }
    }

    private static BlockPos[] getNeighbors(BlockPos pos) {
        return new BlockPos[]{pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()};
    }
}

