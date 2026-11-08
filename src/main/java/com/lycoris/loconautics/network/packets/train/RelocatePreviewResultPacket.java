package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.relocator.SableTrainRelocator;
import com.lycoris.loconautics.trainphysics.rail.RailPick;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RelocatePreviewResultPacket(RailPick pick, boolean blocked) implements CustomPacketPayload {
    public static final Type<RelocatePreviewResultPacket> TYPE = new Type(LoconauticsConstants.id("relocate_preview_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelocatePreviewResultPacket> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        RailPick.STREAM_CODEC.encode(buf, p.pick());
        buf.writeBoolean(p.blocked());
    }, buf -> new RelocatePreviewResultPacket((RailPick)RailPick.STREAM_CODEC.decode(buf), buf.readBoolean()));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RelocatePreviewResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SableTrainRelocator.acceptPreview(packet.pick(), packet.blocked()));
    }
}
