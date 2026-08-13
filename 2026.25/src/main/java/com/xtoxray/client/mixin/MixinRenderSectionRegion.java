/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Pseudo
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
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
@Mixin(targets={"net.minecraft.client.renderer.chunk.RenderSectionRegion"})
public class MixinRenderSectionRegion {
    private static final XrayState XRAY = XrayState.getInstance();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @Inject(method={"getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"}, at={@At(value="RETURN")}, cancellable=true, require=0)
    private void xtoxray$modifyBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!XRAY.isActive()) {
            return;
        }
        BlockState state = (BlockState)cir.getReturnValue();
        if (state == null) {
            return;
        }
        if (state.isAir()) {
            return;
        }
        if (!XRAY.isWhitelisted(state.getBlock()) || XRAY.isBlockDisabled(state.getBlock())) {
            cir.setReturnValue(AIR);
            return;
        }
        int maxDist = XRAY.getOreRenderDistance();
        if (maxDist > 0) {
            double dz;
            double dy;
            double dx;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && (dx = client.player.getX() - (double)pos.getX()) * dx + (dy = client.player.getY() - (double)pos.getY()) * dy + (dz = client.player.getZ() - (double)pos.getZ()) * dz > (double)maxDist * (double)maxDist) {
                cir.setReturnValue(AIR);
            }
        }
    }

}

