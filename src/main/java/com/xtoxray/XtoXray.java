package com.xtoxray;

import com.xtoxray.network.HandshakeNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XtoXray implements ModInitializer {
    public static final String MOD_ID = "xtoxray";
    public static final Logger LOGGER = LoggerFactory.getLogger("xtoxray");

    @Override
    public void onInitialize() {
        ConfigManager.load();
        HandshakeNetworking.registerServer();
        LOGGER.info("XtoXray initialized");
    }
}