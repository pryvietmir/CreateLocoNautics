package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ValveTurnPacket(BlockPos panelPos, int slot, float degrees) implements CustomPacketPayload {
    public static final Type<ValveTurnPacket> TYPE = new Type(LoconauticsConstants.id("valve_turn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ValveTurnPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        ValveTurnPacket::panelPos,
        ByteBufCodecs.VAR_INT,
        ValveTurnPacket::slot,
        ByteBufCodecs.FLOAT,
        ValveTurnPacket::degrees,
        ValveTurnPacket::new
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ValveTurnPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), ValvePart.class, (player, panel, slot, valve) -> {
            float degrees = ValvePart.clamp(packet.degrees());
            panel.replacePart(slot, new ValvePart(degrees, valve.safetyValve(), valve.wheelColor()));
            if ((int)(degrees / 45.0F) != (int)(valve.degrees() / 45.0F)) {
                player.level().playSound(null, packet.panelPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.2F, 0.6F + degrees / 360.0F * 0.5F);
            }
        });
    }
}
