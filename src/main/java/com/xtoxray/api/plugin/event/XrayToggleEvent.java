package com.xtoxray.api.plugin.event;

public record XrayToggleEvent(boolean active) implements PluginEvent {
}
