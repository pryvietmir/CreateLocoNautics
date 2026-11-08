package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.FramePose;
import com.lycoris.loconautics.common.sable.TrackHostFramesClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SubLevelFrameSyncPacket(List<FramePose> frames) implements CustomPacketPayload {
    public static final Type<SubLevelFrameSyncPacket> TYPE = new Type(LoconauticsConstants.id("sublevel_frame_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubLevelFrameSyncPacket> STREAM_CODEC = StreamCodec.of(
        SubLevelFrameSyncPacket::encode, SubLevelFrameSyncPacket::decode
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, SubLevelFrameSyncPacket packet) {
        FriendlyByteBuf out = buf;
        out.writeVarInt(packet.frames.size());

        for (FramePose plate : packet.frames) {
            plate.write(out);
        }
    }

    private static SubLevelFrameSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<FramePose> frames = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            frames.add(FramePose.read(buf));
        }

        return new SubLevelFrameSyncPacket(frames);
    }

    public static void handle(SubLevelFrameSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TrackHostFramesClient.replace(packet.frames()));
    }
}
