package com.xtoxray.client.mixin;

import com.xtoxray.XtoXray;
import com.xtoxray.client.ModBranding;
import com.xtoxray.client.news.NewsOverlay;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {
    protected MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
        at = @At("TAIL"),
        require = 1
    )
    private void xtoxray$renderNews(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        try {
            NewsOverlay.render(this, graphics, graphics.mouseX(), graphics.mouseY());
            ModBranding.render(this, graphics);
        } catch (Throwable t) {
            XtoXray.LOGGER.warn("News-Render fehlgeschlagen", t);
        }
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void xtoxray$clickNews(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        try {
            String url = NewsOverlay.clickUrl(event.x(), event.y(), event.button());
            if (url != null) {
                XtoXray.LOGGER.info("News-Bild geklickt, oeffne {}", url);
                xtoxray$playClickSound();
                try {
                    clickUrlAction(this.minecraft, this, new URI(url));
                } catch (Exception e) {
                    XtoXray.LOGGER.warn("News-Link ungueltig: {}", url, e);
                }
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            XtoXray.LOGGER.warn("News-Klick fehlgeschlagen", t);
        }
    }

    private void xtoxray$playClickSound() {
        try {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        } catch (Throwable t) {
            XtoXray.LOGGER.warn("Klick-Sound fehlgeschlagen", t);
        }
    }
}
