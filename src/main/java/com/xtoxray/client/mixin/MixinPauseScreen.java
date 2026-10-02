package com.xtoxray.client.mixin;

import com.xtoxray.client.gui.XrayConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen extends Screen {
    @Shadow
    private Button disconnectButton;

    protected MixinPauseScreen(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"), require = 1)
    private void xtoxray$addConfigButton(CallbackInfo ci) {
        Button configButton = Button.builder(
            Component.literal("X To Xray Config"),
            btn -> openConfigScreen(new XrayConfigScreen())
        ).width(204).build();

        configButton.setX(this.disconnectButton.getX());
        configButton.setY(this.disconnectButton.getY() + this.disconnectButton.getHeight() + 4);

        this.addRenderableWidget(configButton);
    }

    private void openConfigScreen(Screen screen) {
        com.xtoxray.client.ScreenSwitcher.open(screen);
    }
}
