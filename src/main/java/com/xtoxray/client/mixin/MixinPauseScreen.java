package com.xtoxray.client.mixin;

import com.xtoxray.client.gui.XrayConfigScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen extends Screen {
    protected MixinPauseScreen(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"), require = 1)
    private void xtoxray$addConfigButton(CallbackInfo ci) {
        Button configButton = Button.builder(
            Component.literal("X To Xray Config"),
            btn -> openConfigScreen(new XrayConfigScreen())
        ).width(204).build();

        // Unter dem untersten Button des Menüs platzieren
        AbstractWidget lowest = null;
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w && (lowest == null || w.getBottom() > lowest.getBottom())) {
                lowest = w;
            }
        }
        if (lowest != null) {
            configButton.setX(this.width / 2 - 102);
            configButton.setY(lowest.getBottom() + 4);
        } else {
            configButton.setX(this.width / 2 - 102);
            configButton.setY(this.height / 4 + 120);
        }

        this.addRenderableWidget(configButton);
    }

    private void openConfigScreen(Screen screen) {
        com.xtoxray.client.ScreenSwitcher.open(screen);
    }
}
