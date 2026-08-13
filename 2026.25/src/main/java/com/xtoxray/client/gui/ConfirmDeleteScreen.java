/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.level.block.Block
 */
package com.xtoxray.client.gui;

import com.xtoxray.XrayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

public class ConfirmDeleteScreen
extends Screen {
    private static final int TEXT = -3355444;
    private static final int TEXT_BRIGHT = -1;
    private static final int SEPARATOR = -12303292;
    private static final int BTN_BG = -14803426;
    private static final int BTN_BG_HOVER = -13882324;
    private static final int BTN_BORDER = -12961222;
    private final Screen parent;
    private final Block block;
    private int hoveredBtn = -1;
    private int pw;
    private int ph;

    public ConfirmDeleteScreen(Screen parent, Block block) {
        super((Component)Component.literal((String)"Confirm Delete"));
        this.parent = parent;
        this.block = block;
    }

    protected void init() {
        this.pw = Math.min(this.width - 40, 300);
        this.ph = Math.min(this.height - 40, 100);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int px = (this.width - this.pw) / 2;
        int py = (this.height - this.ph) / 2;
        g.fill(0, 0, this.width, this.height, -872415232);
        g.fill(px, py, px + this.pw, py + this.ph, -267514354);
        g.fill(px, py, px + this.pw, py + 2, -14013910);
        g.fill(px, py + this.ph - 1, px + this.pw, py + this.ph, -12303292);
        g.fill(px, py, px + 1, py + this.ph, -12303292);
        g.fill(px + this.pw - 1, py, px + this.pw, py + this.ph, -12303292);
        String msg = "Remove " + this.block.getName().getString() + "?";
        int tw = this.font.width(msg);
        g.text(this.font, (Component)Component.literal((String)msg), (this.width - tw) / 2, py + 20, -1);
        int btnY = py + 50;
        int btnW = 90;
        this.drawBtn(g, this.width / 2 - btnW - 5, btnY, btnW, "Remove", 0, mouseX, mouseY);
        this.drawBtn(g, this.width / 2 + 5, btnY, btnW, "Cancel", 1, mouseX, mouseY);
    }

    private void drawBtn(GuiGraphicsExtractor g, int x, int y, int w, String label, int id, int mouseX, int mouseY) {
        boolean hovered;
        boolean bl = hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20;
        if (hovered) {
            this.hoveredBtn = id;
        }
        int bg = hovered ? -13882324 : -14803426;
        int border = hovered ? -1 : -12961222;
        g.fill(x, y, x + w, y + 20, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + 19, x + w, y + 20, border);
        g.fill(x, y, x + 1, y + 20, border);
        g.fill(x + w - 1, y, x + w, y + 20, border);
        int tw = this.font.width(label);
        g.text(this.font, (Component)Component.literal((String)label), x + (w - tw) / 2, y + 6, -1);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int)event.x();
        int my = (int)event.y();
        int py = (this.height - this.ph) / 2;
        int btnY = py + 50;
        int btnW = 90;
        if (event.button() == 0 && this.hoveredBtn >= 0) {
            if (this.hoveredBtn == 0) {
                XrayState.getInstance().getWhitelist().remove(this.block);
                XrayState.getInstance().save();
                com.xtoxray.client.VersionAdapter.setScreen(Minecraft.getInstance(), this.parent);
            } else {
                com.xtoxray.client.VersionAdapter.setScreen(Minecraft.getInstance(), this.parent);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    public void mouseMoved(double mouseX, double mouseY) {
        this.hoveredBtn = -1;
        int mx = (int)mouseX;
        int my = (int)mouseY;
        int py = (this.height - this.ph) / 2;
        int btnY = py + 50;
        int btnW = 90;
        int leftX = this.width / 2 - btnW - 5;
        int rightX = this.width / 2 + 5;
        if (my >= btnY && my < btnY + 20) {
            if (mx >= leftX && mx < leftX + btnW) {
                this.hoveredBtn = 0;
            }
            if (mx >= rightX && mx < rightX + btnW) {
                this.hoveredBtn = 1;
            }
        }
        super.mouseMoved(mouseX, mouseY);
    }

    public boolean shouldPause() {
        return false;
    }
}

