package com.xtoxray.network;

/**
 * Public API for networking — ZERO MC imports.
 * Delegates everything to NetworkHelper (loaded via Class.forName).
 * If NetworkHelper can't load (missing MC classes on older versions),
 * all methods gracefully degrade.
 */
public class XrayPayloads {

    private static volatile Boolean available;

    /**
     * Check if networking is available.
     * Triggers Class.forName("NetworkHelper") — if that fails, networking is disabled.
     */
    public static boolean isAvailable() {
        if (available != null) return available;
        synchronized (XrayPayloads.class) {
            if (available != null) return available;
            try {
                Class.forName("com.xtoxray.network.NetworkHelper");
                available = IdentifierHelper.isAvailable();
            } catch (Throwable t) {
                available = false;
            }
        }
        return available;
    }

    /** Register server-side packet handlers. Called from XtoXray.onInitialize(). */
    public static void registerServer() {
        if (!isAvailable()) return;
        try {
            Class.forName("com.xtoxray.network.NetworkHelper")
                .getMethod("registerServer")
                .invoke(null);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to register server networking", t);
        }
    }

    /** Register client-side packet handlers. Called from XtoXrayClient.onInitializeClient(). */
    public static void registerClient() {
        if (!isAvailable()) return;
        try {
            Class.forName("com.xtoxray.network.NetworkHelper")
                .getMethod("registerClient")
                .invoke(null);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to register client networking", t);
        }
    }

    /** Send vein miner sync packet. Called from XrayToggleHandler and XrayModMenu. */
    public static void sendSyncVeinMiner(boolean active) {
        if (!isAvailable()) return;
        try {
            Class.forName("com.xtoxray.network.NetworkHelper")
                .getMethod("sendSyncVeinMiner", boolean.class)
                .invoke(null, active);
        } catch (Throwable ignored) {}
    }
}

