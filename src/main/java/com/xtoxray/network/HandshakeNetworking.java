package com.xtoxray.network;

import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HandshakeNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("xtoxray-handshake");
    private static final int PROTOCOL_VERSION = 1;
    public static final Identifier HANDSHAKE_CHANNEL = Identifier.fromNamespaceAndPath("xtoxray", "handshake");

    private HandshakeNetworking() {
    }

    public static void registerServer() {
        ServerLoginNetworking.registerGlobalReceiver(HANDSHAKE_CHANNEL, (server, listener, understood, buf, synchronizer, responseSender) -> {
            if (buf.readableBytes() < 4) {
                LOGGER.warn("XtoXray handshake payload too short ({} bytes), ignoring", buf.readableBytes());
                return;
            }

            int clientVersion = buf.readInt();
            if (clientVersion != PROTOCOL_VERSION) {
                LOGGER.warn("XtoXray client protocol mismatch: expected {}, got {}", PROTOCOL_VERSION, clientVersion);
            }
        });

        ServerLoginConnectionEvents.QUERY_START.register((listener, server, sender, synchronizer) -> {
            sender.sendPacket(HANDSHAKE_CHANNEL, writePayload(PROTOCOL_VERSION));
        });
    }

    public static void registerClient() {
        ClientLoginConnectionEvents.INIT.register((listener, client) -> {
            HandshakeState.getInstance().resetOnConnect();
        });

        ClientLoginNetworking.registerGlobalReceiver(HANDSHAKE_CHANNEL, (client, listener, buf, callbacksConsumer) -> {
            if (buf.readableBytes() < 4) {
                return CompletableFuture.completedFuture(null);
            }

            int serverVersion = buf.readInt();
            if (serverVersion != PROTOCOL_VERSION) {
                return CompletableFuture.completedFuture(null);
            }

            HandshakeState.getInstance().setServerHasMod(true);
            return CompletableFuture.completedFuture(writePayload(PROTOCOL_VERSION));
        });
    }

    private static FriendlyByteBuf writePayload(int version) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(version);
        return buf;
    }

}