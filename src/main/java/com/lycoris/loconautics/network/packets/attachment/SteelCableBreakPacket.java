package com.lycoris.loconautics.network.packets.attachment;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandDriver;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;

public record SteelCableBreakPacket(UUID strandUUID) implements CustomPacketPayload {
    private static final double MAX_BREAK_REACH = 16.0;
    public static final Type<SteelCableBreakPacket> TYPE = new Type(LoconauticsConstants.id("steel_cable_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SteelCableBreakPacket> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, SteelCableBreakPacket::strandUUID, SteelCableBreakPacket::new
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SteelCableBreakPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
                SteelCableStrandManager var8 = SteelCableStrandManager.get(level);
                SteelCableRopeManager ropes = SteelCableRopeManager.get(level);
                if (var8 != null && ropes != null) {
                    SteelCableStrand strand = var8.get(packet.strandUUID());
                    if (strand != null && withinReach(player, strand)) {
                        SteelCableStrandDriver.breakStrand(level, var8, ropes, strand, player);
                    }
                }
            }
        });
    }

    private static boolean withinReach(ServerPlayer player, SteelCableStrand strand) {
        if (strand.points == null) {
            return false;
        }

        Vec3 eye = player.getEyePosition();

        for (Vector3d p : strand.points) {
            if (eye.distanceToSqr(p.x, p.y, p.z) <= 256.0) {
                return true;
            }
        }

        return false;
    }
}
