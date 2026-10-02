package com.xtoxray.client.mixin;

import com.xtoxray.XrayState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"net.caffeinemc.mods.sodium.client.world.LevelSlice"})
public class MixinSodiumLevelSlice {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @Inject(
        method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void xtoxray$modifyBlockState(int blockX, int blockY, int blockZ, CallbackInfoReturnable<BlockState> cir) {
        if (!XrayState.getInstance().isActive()) return;
        BlockState state = cir.getReturnValue();
        if (state == null || state.isAir()) return;
        if (!XrayState.getInstance().isWhitelisted(state.getBlock())) {
            cir.setReturnValue(AIR);
        }
    }

    @Inject(
        method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void xtoxray$modifyBlockStateFromPos(net.minecraft.core.BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!XrayState.getInstance().isActive()) return;
        BlockState state = cir.getReturnValue();
        if (state == null || state.isAir()) return;
        if (!XrayState.getInstance().isWhitelisted(state.getBlock())) {
            cir.setReturnValue(AIR);
        }
    }
}
