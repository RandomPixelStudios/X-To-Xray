package com.xtoxray.api.plugin.event;

/** Calls public @Subscribe methods with a single PluginEvent parameter, on the client thread. */
public interface EventBus {
    void register(Listener listener);

    void unregister(Listener listener);
}
