package com.lycoris.loconautics.network.packets.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.ConductorHats;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BlazeConductorHatPacket(UUID subLevelId, BlockPos pos, boolean hat) implements CustomPacketPayload {
    public static final Type<BlazeConductorHatPacket> TYPE = new Type(LoconauticsConstants.id("blaze_conductor_hat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlazeConductorHatPacket> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        BlazeConductorHatPacket::subLevelId,
        BlockPos.STREAM_CODEC,
        BlazeConductorHatPacket::pos,
        ByteBufCodecs.BOOL,
        BlazeConductorHatPacket::hat,
        BlazeConductorHatPacket::new
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlazeConductorHatPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ConductorHats.setDriver(packet.subLevelId(), packet.pos(), packet.hat()));
    }
}
