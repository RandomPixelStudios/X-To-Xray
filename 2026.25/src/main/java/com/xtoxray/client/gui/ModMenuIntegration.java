/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.terraformersmc.modmenu.api.ConfigScreenFactory
 *  com.terraformersmc.modmenu.api.ModMenuApi
 *  net.minecraft.client.gui.screens.Screen
 */
package com.xtoxray.client.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.xtoxray.client.gui.XrayModMenu;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration
implements ModMenuApi {
    public ConfigScreenFactory<? extends Screen> getModConfigScreenFactory() {
        return XrayModMenu::new;
    }
}

