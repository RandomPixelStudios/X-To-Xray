/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.layouts.GridLayout
 *  net.minecraft.client.gui.layouts.GridLayout$RowHelper
 *  net.minecraft.client.gui.layouts.LayoutElement
 *  net.minecraft.client.gui.screens.PauseScreen
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.LocalCapture
 */
package com.xtoxray.client.mixin;

import com.xtoxray.client.gui.XrayModMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value={PauseScreen.class})
public class MixinPauseScreen {
    @Inject(method={"createPauseMenu"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V")}, locals=LocalCapture.CAPTURE_FAILSOFT, require=0)
    private void addXrayButton(CallbackInfo ci, GridLayout layout, GridLayout.RowHelper rowHelper) {
        rowHelper.addChild((LayoutElement)Button.builder((Component)Component.literal((String)"X To Xray"), btn -> com.xtoxray.client.VersionAdapter.setScreen(Minecraft.getInstance(), new XrayModMenu((Screen)(Object)this))).width(204).build(), 2);
    }
}

