package com.xtoxray.api.plugin;

import com.xtoxray.api.plugin.event.EventBus;

public interface PluginContext {
    PluginDescriptor getDescriptor();

    PluginLogger getLogger();

    EventBus getEventBus();

    /** Requires the "storage" permission, otherwise throws SecurityException. */
    PluginStorage getStorage();

    /** Requires "xray.read"; changing the whitelist requires "xray.whitelist". */
    XrayAccess getXray();
}
