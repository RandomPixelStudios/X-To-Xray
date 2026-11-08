package com.xtoxray.client.gui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** From 26.4 screens render with extractRenderState(graphics) only; the mouse position is read from the game. */
public final class GuiCompat {
    private GuiCompat() {
    }

    public static int mouseX() {
        Minecraft mc = Minecraft.getInstance();
        return (int) mc.mouseHandler.getScaledXPos(mc.getWindow());
    }

    public static int mouseY() {
        Minecraft mc = Minecraft.getInstance();
        return (int) mc.mouseHandler.getScaledYPos(mc.getWindow());
    }

    public static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /** Calls Screen.extractRenderState(graphics) as a super call; this method only exists from 26.4 on. */
    static MethodHandle superRender(MethodHandles.Lookup lookup, Class<?> caller) throws ReflectiveOperationException {
        return lookup.findSpecial(Screen.class, "extractRenderState",
            MethodType.methodType(void.class, GuiGraphicsExtractor.class), caller);
    }

    /** Calls Screen.extractRenderState(graphics, mouseX, mouseY, partialTick) as a super call; before 26.4. */
    static MethodHandle superRenderOld(MethodHandles.Lookup lookup, Class<?> caller) throws ReflectiveOperationException {
        return lookup.findSpecial(Screen.class, "extractRenderState",
            MethodType.methodType(void.class, GuiGraphicsExtractor.class, int.class, int.class, float.class), caller);
    }
}
