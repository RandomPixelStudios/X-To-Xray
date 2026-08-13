package com.xtoxray.client.mixin;

import com.xtoxray.client.ContainerViewHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGuiRender {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void xtoxray$renderContainerOverlay(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        try {
            ContainerViewHandler.renderOverlay(graphics, delta);
        } catch (Throwable t) {
            // Silently ignore rendering errors
        }
    }
}
