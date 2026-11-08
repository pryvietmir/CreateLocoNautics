package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LeverSetPacket(BlockPos panelPos, int slot, int power) implements CustomPacketPayload {
    public static final Type<LeverSetPacket> TYPE = new Type(LoconauticsConstants.id("lever_set"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LeverSetPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        LeverSetPacket::panelPos,
        ByteBufCodecs.VAR_INT,
        LeverSetPacket::slot,
        ByteBufCodecs.VAR_INT,
        LeverSetPacket::power,
        LeverSetPacket::new
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LeverSetPacket packet, IPayloadContext context) {
        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), LeverPart.class, (player, panel, slot, lever) -> {
            int power = LeverPart.clamp(packet.power());
            if (power != lever.power()) {
                panel.replacePart(slot, new LeverPart(power, lever.link(), lever.body(), lever.knobColor(), lever.style(), lever.options()));
                player.level().playSound(null, packet.panelPos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.25F, 0.9F + power / 15.0F * 0.6F);
            }
        });
    }
}
