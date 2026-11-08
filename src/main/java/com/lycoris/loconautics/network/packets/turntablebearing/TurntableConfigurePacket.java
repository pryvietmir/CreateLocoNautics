package com.lycoris.loconautics.network.packets.turntablebearing;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlockEntity;
import com.lycoris.loconautics.network.PacketTargets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TurntableConfigurePacket(BlockPos pos, List<TurntableInstruction> instructions, TurntableTriggerMode trigger) implements CustomPacketPayload {
    private static final double MAX_REACH_SQUARED = 64.0;
    public static final Type<TurntableConfigurePacket> TYPE = new Type(LoconauticsConstants.id("turntable_configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TurntableConfigurePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        TurntableConfigurePacket::pos,
        TurntableInstruction.LIST_STREAM_CODEC,
        TurntableConfigurePacket::instructions,
        TurntableTriggerMode.STREAM_CODEC,
        TurntableConfigurePacket::trigger,
        TurntableConfigurePacket::new
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TurntableConfigurePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.isSpectator()) {
                    if (!(player.distanceToSqr(packet.pos().getCenter()) > 64.0)) {
                        TurntableBearingBlockEntity bearing = PacketTargets.blockEntity(player.level(), packet.pos(), TurntableBearingBlockEntity.class);
                        if (bearing != null) {
                            bearing.applyConfiguration(packet.instructions(), packet.trigger());
                        }
                    }
                }
            }
        });
    }
}
