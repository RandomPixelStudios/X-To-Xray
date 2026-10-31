package com.xtoxray.api.plugin.event;

/** Fired after the veinminer broke additional blocks; count excludes the block mined by hand. */
public record VeinMinedEvent(String blockId, int count) implements PluginEvent {
}
