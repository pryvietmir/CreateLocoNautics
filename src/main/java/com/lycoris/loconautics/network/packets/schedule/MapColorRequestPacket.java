package com.lycoris.loconautics.network.packets.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.map.MapColorStorage;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MapColorRequestPacket(List<long[]> chunks) implements CustomPacketPayload {
    public static final Type<MapColorRequestPacket> TYPE = new Type(LoconauticsConstants.id("map_color_request"));
    public static final int MAX_CHUNKS = 400;
    public static final StreamCodec<ByteBuf, MapColorRequestPacket> STREAM_CODEC = new StreamCodec<ByteBuf, MapColorRequestPacket>() {
        public MapColorRequestPacket decode(ByteBuf buf) {
            int size = buf.readInt();
            if (size >= 0 && size <= 400) {
                List<long[]> chunks = new ArrayList<>(size);

                for (int i = 0; i < size; i++) {
                    chunks.add(new long[]{buf.readInt(), buf.readInt()});
                }

                return new MapColorRequestPacket(chunks);
            } else {
                throw new DecoderException("map color request of " + size + " chunks, cap is 400");
            }
        }

        public void encode(ByteBuf buf, MapColorRequestPacket packet) {
            buf.writeInt(packet.chunks().size());

            for (long[] c : packet.chunks()) {
                buf.writeInt((int)c[0]);
                buf.writeInt((int)c[1]);
            }
        }
    };

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MapColorRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> {
                ServerLevel level = player.serverLevel();
                MapColorStorage store = MapColorStorage.get(level);
                List<MapColorResponsePacket.Entry> out = new ArrayList<>();

                for (MapColorStorage.MapColorEntry e : store.readBatch(packet.chunks())) {
                    out.add(new MapColorResponsePacket.Entry(e.chunkX(), e.chunkZ(), e.colors()));
                }

                if (!out.isEmpty()) {
                    PacketDistributor.sendToPlayer(player, new MapColorResponsePacket(out), new CustomPacketPayload[0]);
                }
            });
        }
    }
}
