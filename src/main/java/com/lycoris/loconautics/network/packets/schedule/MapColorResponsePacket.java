package com.lycoris.loconautics.network.packets.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.map.client.FullscreenMapScreen;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MapColorResponsePacket(List<MapColorResponsePacket.Entry> entries) implements CustomPacketPayload {
    public static final Type<MapColorResponsePacket> TYPE = new Type(LoconauticsConstants.id("map_color_response"));
    public static final StreamCodec<ByteBuf, MapColorResponsePacket> STREAM_CODEC = new StreamCodec<ByteBuf, MapColorResponsePacket>() {
        public MapColorResponsePacket decode(ByteBuf buf) {
            int size = buf.readInt();
            if (size >= 0 && size <= 400) {
                List<MapColorResponsePacket.Entry> entries = new ArrayList<>(size);

                for (int i = 0; i < size; i++) {
                    int cx = buf.readInt();
                    int cz = buf.readInt();
                    byte[] colors = new byte[256];
                    buf.readBytes(colors);
                    entries.add(new MapColorResponsePacket.Entry(cx, cz, colors));
                }

                return new MapColorResponsePacket(entries);
            } else {
                throw new DecoderException("map color response of " + size + " chunks");
            }
        }

        public void encode(ByteBuf buf, MapColorResponsePacket packet) {
            buf.writeInt(packet.entries().size());

            for (MapColorResponsePacket.Entry e : packet.entries()) {
                buf.writeInt(e.chunkX());
                buf.writeInt(e.chunkZ());
                buf.writeBytes(e.colors());
            }
        }
    };

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MapColorResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> FullscreenMapScreen.acceptColors(packet.entries()));
    }

    public record Entry(int chunkX, int chunkZ, byte[] colors) {
    }
}
