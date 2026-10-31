package com.example.template;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.PluginLogger;
import com.xtoxray.api.plugin.event.BlockMinedEvent;
import com.xtoxray.api.plugin.event.Subscribe;
import java.io.IOException;

public class ExamplePlugin implements Plugin {
    private PluginContext context;
    private PluginLogger logger;
    private int mined;

    @Override
    public void onEnable(PluginContext context) {
        this.context = context;
        this.logger = context.getLogger();
        try {
            mined = context.getStorage().read("mined.txt").map(Integer::parseInt).orElse(0);
        } catch (IOException e) {
            logger.warn("Could not read saved count: " + e.getMessage());
        }
        context.getEventBus().register(this);
    }

    @Override
    public void onDisable(PluginContext context) {
        try {
            context.getStorage().write("mined.txt", Integer.toString(mined));
        } catch (IOException e) {
            logger.warn("Could not save count: " + e.getMessage());
        }
    }

    @Subscribe
    public void onBlockMined(BlockMinedEvent event) {
        mined++;
    }

    @Override
    public String getSidebarTabName() {
        return "Example";
    }

    @Override
    public String[] getSidebarTabContent() {
        return new String[]{"Blocks mined: " + mined};
    }
}
