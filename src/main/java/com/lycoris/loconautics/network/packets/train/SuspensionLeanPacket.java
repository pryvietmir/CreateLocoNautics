package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

public record SuspensionLeanPacket(UUID subLevelId, float x, float y, float z, float w) implements CustomPacketPayload {
    public static final Type<SuspensionLeanPacket> TYPE = new Type(LoconauticsConstants.id("suspension_lean"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SuspensionLeanPacket> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUUID(p.subLevelId());
        buf.writeFloat(p.x());
        buf.writeFloat(p.y());
        buf.writeFloat(p.z());
        buf.writeFloat(p.w());
    }, buf -> new SuspensionLeanPacket(buf.readUUID(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SuspensionLeanPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SableTrainClientRegistry.putLean(packet.subLevelId(), new Quaternionf(packet.x(), packet.y(), packet.z(), packet.w())));
    }
}
