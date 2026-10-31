package com.xtoxray.client.mixin;

import com.xtoxray.veinminer.VeinminerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {
    @Unique
    private static Block xtoxray$cachedBlock;

    @Inject(method = "destroyBlock", at = @At("HEAD"), require = 1)
    private void xtoxray$captureBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        xtoxray$cachedBlock = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir()) {
                xtoxray$cachedBlock = state.getBlock();
            }
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"), require = 1)
    private void xtoxray$onBlockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && xtoxray$cachedBlock != null) {
            com.xtoxray.plugin.PluginManager.getInstance().post(new com.xtoxray.api.plugin.event.BlockMinedEvent(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(xtoxray$cachedBlock).toString(), pos.getX(), pos.getY(), pos.getZ()));
            VeinminerHandler.getInstance().onBlockBroken(pos, xtoxray$cachedBlock);
        }
    }
}
