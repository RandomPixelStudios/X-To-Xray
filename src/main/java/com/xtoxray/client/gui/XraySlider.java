package com.xtoxray.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class XraySlider extends AbstractSliderButton {
    private final OnValueChange onChange;
    private final String label;
    private final int min;
    private final int max;

    public interface OnValueChange {
        void onValueChange(int value);
    }

    public XraySlider(int x, int y, int width, int height, String label, int min, int max, int current, OnValueChange onChange) {
        super(x, y, width, height, Component.literal(label + ": " + current), (double) (current - min) / (max - min));
        this.onChange = onChange;
        this.label = label;
        this.min = min;
        this.max = max;
    }

    protected void updateMessage() {
        int val = (int) Math.round(this.value * (max - min) + min);
        this.setMessage(Component.literal(this.label + ": " + val));
    }

    protected void applyValue() {
        if (this.onChange != null) {
            int val = (int) Math.round(this.value * (max - min) + min);
            this.onChange.onValueChange(val);
        }
    }
}
