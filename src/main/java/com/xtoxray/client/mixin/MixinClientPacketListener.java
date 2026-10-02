package com.xtoxray.client.mixin;

import com.xtoxray.ConfigManager;
import com.xtoxray.XrayState;
import com.xtoxray.client.XrayToggleHandler;
import com.xtoxray.network.HandshakeState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl.class)
public class MixinClientPacketListener {
    @Inject(method = "onDisconnect", at = @At("HEAD"), require = 1)
    private void xtoxray$onDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        HandshakeState.getInstance().setServerHasMod(false);
        XrayState state = XrayState.getInstance();
        if (state.isActive()) {
            state.toggle();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            }
            XrayToggleHandler.rebuildAllSections(mc);
            ConfigManager.save();
        }
    }
}
