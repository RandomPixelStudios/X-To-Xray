package com.xtoxray.api.plugin.event;

public record BlockMinedEvent(String blockId, int x, int y, int z) implements PluginEvent {
}
