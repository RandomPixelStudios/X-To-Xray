package com.xtoxray.client.mixin;

import com.xtoxray.XrayState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Sodium's LevelSlice — intercepts block state lookups during
 * Sodium's chunk meshing pipeline so Xray works with Sodium installed.
 *
 * Both overloads are targeted because Sodium may call either:
 *  - getBlockState(int, int, int)  — the internal overload
 *  - getBlockState(BlockPos)       — the BlockPos convenience overload
 */
@Pseudo
@Mixin(targets = {"net.caffeinemc.mods.sodium.client.world.LevelSlice"})
public class MixinSodiumLevelSlice {

    private static final XrayState XRAY = XrayState.getInstance();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // ─── Overload 1: getBlockState(int, int, int) ──────────────────────

    @Inject(
        method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void xtoxray$modifyBlockState(int blockX, int blockY, int blockZ,
            CallbackInfoReturnable<BlockState> cir) {
        xtoxray$applyXray(blockX, blockY, blockZ, cir);
    }

    // ─── Overload 2: getBlockState(BlockPos) ───────────────────────────

    @Inject(
        method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void xtoxray$modifyBlockStateFromPos(BlockPos pos,
            CallbackInfoReturnable<BlockState> cir) {
        xtoxray$applyXray(pos.getX(), pos.getY(), pos.getZ(), cir);
    }

    // ─── Shared logic ──────────────────────────────────────────────────

    @Unique
    private static void xtoxray$applyXray(int bx, int by, int bz,
            CallbackInfoReturnable<BlockState> cir) {
        if (!XRAY.isActive()) {
            return;
        }
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }

        if (state.isAir()) {
            return;
        }

        // Non-whitelisted / disabled blocks → transparent
        if (!XRAY.isWhitelisted(state.getBlock()) || XRAY.isBlockDisabled(state.getBlock())) {
            cir.setReturnValue(AIR);
            return;
        }

        // Ore distance culling
        int maxDist = XRAY.getOreRenderDistance();
        if (maxDist > 0) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                double dx = client.player.getX() - (double) bx;
                double dy = client.player.getY() - (double) by;
                double dz = client.player.getZ() - (double) bz;
                if (dx * dx + dy * dy + dz * dz > (double) maxDist * (double) maxDist) {
                    cir.setReturnValue(AIR);
                }
            }
        }
    }
}

