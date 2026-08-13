package com.xtoxray.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import com.xtoxray.XrayState;

/**
 * Central networking hub — ONLY loaded via Class.forName() from XrayPayloads.
 * Contains ALL MC-specific networking imports so no other class needs them.
 * If any MC class is missing (e.g. on older versions), this class fails to load
 * and XrayPayloads.isAvailable() returns false.
 */
public class NetworkHelper {

    // ── Packet records ──────────────────────────────────────────

    public record SyncVeinMinerC2S(boolean active) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncVeinMinerC2S> TYPE =
            new CustomPacketPayload.Type<>((Identifier) IdentifierHelper.create("xtoxray:sync_vein"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncVeinMinerC2S> CODEC = StreamCodec.of(
            (buf, val) -> buf.writeBoolean(val.active),
            buf -> new SyncVeinMinerC2S(buf.readBoolean())
        );
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HandshakeS2C() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<HandshakeS2C> TYPE =
            new CustomPacketPayload.Type<>((Identifier) IdentifierHelper.create("xtoxray:handshake_ack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HandshakeS2C> CODEC = StreamCodec.unit(new HandshakeS2C());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HandshakeC2S() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<HandshakeC2S> TYPE =
            new CustomPacketPayload.Type<>((Identifier) IdentifierHelper.create("xtoxray:handshake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HandshakeC2S> CODEC = StreamCodec.unit(new HandshakeC2S());
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Server registration ─────────────────────────────────────

    public static void registerServer() {
        PayloadTypeRegistry.serverboundPlay().register(HandshakeC2S.TYPE, HandshakeC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HandshakeS2C.TYPE, HandshakeS2C.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SyncVeinMinerC2S.TYPE, SyncVeinMinerC2S.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(HandshakeC2S.TYPE, (payload, context) ->
            context.server().execute(() -> {
                if (context.player() != null) {
                    ServerPlayNetworking.send((ServerPlayer) context.player(), new HandshakeS2C());
                }
            })
        );
        ServerPlayNetworking.registerGlobalReceiver(SyncVeinMinerC2S.TYPE, (payload, context) ->
            context.server().execute(() -> XrayState.getInstance().setVeinMiner(payload.active()))
        );
    }

    // ── Client registration ─────────────────────────────────────

    public static void registerClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sender.sendPacket(new SyncVeinMinerC2S(XrayState.getInstance().isVeinMiner()));
            sender.sendPacket(new HandshakeC2S());
        });
        ClientPlayNetworking.registerGlobalReceiver(HandshakeS2C.TYPE,
            (payload, context) -> XrayState.getInstance().setServerAllowsXray(true));
    }

    // ── Send helpers ────────────────────────────────────────────

    public static void sendSyncVeinMiner(boolean active) {
        ClientPlayNetworking.send(new SyncVeinMinerC2S(active));
    }
}
