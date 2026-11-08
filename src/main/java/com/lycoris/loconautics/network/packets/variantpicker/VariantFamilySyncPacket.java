package com.lycoris.loconautics.network.packets.variantpicker;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.variantpicker.BlockVariantRegistry;
import com.lycoris.loconautics.content.variantpicker.data.LoadedVariantFamily;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record VariantFamilySyncPacket(List<LoadedVariantFamily> families) implements CustomPacketPayload {
    public static final Type<VariantFamilySyncPacket> TYPE = new Type(LoconauticsConstants.id("variant_family_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VariantFamilySyncPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> LoadedVariantFamily.write(buf, packet.families()), buf -> new VariantFamilySyncPacket(LoadedVariantFamily.read(buf))
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VariantFamilySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> BlockVariantRegistry.rebuild(packet.families()));
    }
}
