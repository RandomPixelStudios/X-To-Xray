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
        
        context.getEventBus().register(this);
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

    @Subscribe
    public void onSomeEvent(Object event) {
        logger.info("Received event: " + event);
    }
}