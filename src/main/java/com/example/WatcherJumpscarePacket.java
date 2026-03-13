package com.example;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent server → client to trigger the jumpscare screen overlay.
 * Carries no data — it is purely a fire-and-forget signal.
 */
public record WatcherJumpscarePacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WatcherJumpscarePacket> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("customitemsk", "watcher_jumpscare"));

    // No fields → codec reads/writes nothing and always returns the singleton
    public static final StreamCodec<ByteBuf, WatcherJumpscarePacket> CODEC =
            StreamCodec.unit(new WatcherJumpscarePacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
