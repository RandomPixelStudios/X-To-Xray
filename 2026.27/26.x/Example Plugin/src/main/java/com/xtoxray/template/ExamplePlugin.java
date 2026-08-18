package com.xtoxray.template;

import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginContext;
import com.xtoxray.api.plugin.PluginLogger;
import com.xtoxray.api.plugin.event.Listener;
import com.xtoxray.api.plugin.event.Subscribe;

public class ExamplePlugin implements Plugin, Listener {
    private PluginLogger logger;

    @Override
    public void onEnable(PluginContext context) {
        this.logger = context.getLogger();
        logger.info("ExamplePlugin enabled!");
        
        // Register event listener
        context.getEventBus().register(this);
        
        // Example: access data folder
        // Path dataFolder = context.getDataFolder();
        // Files.createDirectories(dataFolder);
    }

    @Override
    public void onDisable(PluginContext context) {
        logger.info("ExamplePlugin disabled!");
    }

    @Override
    public String getName() {
        return "ExamplePlugin";
    }

    @Override
    public String getDescription() {
        return "A sample plugin demonstrating the XtoXray API";
    }

    @Override
    public String getAuthor() {
        return "YourName";
    }

    // Example event handler
    @Subscribe
    public void onSomeEvent(Object event) {
        logger.info("Received event: " + event);
    }
}