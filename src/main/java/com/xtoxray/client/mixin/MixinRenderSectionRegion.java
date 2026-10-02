package com.xtoxray.client.mixin;

import com.xtoxray.XrayState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"net.minecraft.client.renderer.chunk.RenderSectionRegion"})
public class MixinRenderSectionRegion {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @Inject(
        method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1
    )
    private void xtoxray$modifyBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        XrayState state = XrayState.getInstance();
        if (!state.isActive() || !state.isRenderingEnabled()) return;
        BlockState blockState = cir.getReturnValue();
        if (blockState == null || blockState.isAir()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int maxBlocks = state.getRenderDistance() * 16;
        BlockPos playerPos = mc.player.blockPosition();
        if (Math.abs(pos.getX() - playerPos.getX()) > maxBlocks ||
            Math.abs(pos.getY() - playerPos.getY()) > maxBlocks ||
            Math.abs(pos.getZ() - playerPos.getZ()) > maxBlocks) {
            return;
        }
        if (!state.isWhitelisted(blockState.getBlock())) {
            cir.setReturnValue(AIR);
        }
    }
}
