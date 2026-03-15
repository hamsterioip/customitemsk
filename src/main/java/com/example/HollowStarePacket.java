package com.example;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent server → client when The Hollow's stare reaches full intensity.
 * Triggers HollowVigilOverlay — a HUD-level creeping darkness that bypasses
 * any fullbright / gamma mod because it is drawn directly over the screen.
 */
public record HollowStarePacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HollowStarePacket> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("customitemsk", "hollow_stare"));

    public static final StreamCodec<ByteBuf, HollowStarePacket> CODEC =
            StreamCodec.unit(new HollowStarePacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
