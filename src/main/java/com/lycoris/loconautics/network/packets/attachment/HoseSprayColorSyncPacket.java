package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.spray.HoseSprayColors;
import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HoseSprayColorSyncPacket(Map<ResourceLocation, int[]> byFluid) implements CustomPacketPayload {
    public static final Type<HoseSprayColorSyncPacket> TYPE = new Type(LoconauticsConstants.id("hose_spray_color_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HoseSprayColorSyncPacket> STREAM_CODEC = StreamCodec.of(
        HoseSprayColorSyncPacket::write, HoseSprayColorSyncPacket::read
    );
    private static final int MAX_FLUIDS = 4096;
    private static final int MAX_COLORS = 64;

    private static void write(RegistryFriendlyByteBuf buf, HoseSprayColorSyncPacket p) {
        buf.writeVarInt(p.byFluid.size());

        for (Entry<ResourceLocation, int[]> e : p.byFluid.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            int[] c = e.getValue();
            buf.writeVarInt(c.length);

            for (int v : c) {
                buf.writeInt(v);
            }
        }
    }

    private static HoseSprayColorSyncPacket read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n >= 0 && n <= 4096) {
            Map<ResourceLocation, int[]> map = new LinkedHashMap<>();

            for (int i = 0; i < n; i++) {
                ResourceLocation id = buf.readResourceLocation();
                int len = buf.readVarInt();
                if (len < 0 || len > 64) {
                    throw new DecoderException("spray color sync for " + id + " with " + len + " colors");
                }

                int[] c = new int[len];

                for (int j = 0; j < len; j++) {
                    c[j] = buf.readInt();
                }

                map.put(id, c);
            }

            return new HoseSprayColorSyncPacket(map);
        } else {
            throw new DecoderException("spray color sync for " + n + " fluids");
        }
    }

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HoseSprayColorSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> HoseSprayColors.setDatapack(packet.byFluid()));
    }
}
