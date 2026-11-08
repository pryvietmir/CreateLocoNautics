package com.lycoris.loconautics.network.packets.train;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.relocator.RelocatorItemData;
import com.lycoris.loconautics.trainphysics.rail.RailPick;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.spawn.TrainStructures;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RelocatePreviewPacket(boolean stored, boolean offHand, UUID subLevelId, RailPick pick, Vec3 lookAngle) implements CustomPacketPayload {
    public static final Type<RelocatePreviewPacket> TYPE = new Type(LoconauticsConstants.id("relocate_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelocatePreviewPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBoolean(p.stored());
            buf.writeBoolean(p.offHand());
            buf.writeUUID(p.subLevelId());
            RailPick.STREAM_CODEC.encode(buf, p.pick());
            buf.writeDouble(p.lookAngle().x);
            buf.writeDouble(p.lookAngle().y);
            buf.writeDouble(p.lookAngle().z);
        },
        buf -> new RelocatePreviewPacket(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readUUID(),
            (RailPick)RailPick.STREAM_CODEC.decode(buf),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
        )
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RelocatePreviewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                boolean blocked = SableTrainSpawner.withinRelocateReach(player, packet.pick().pos()) && blockedByTrain(player, packet);
                PacketDistributor.sendToPlayer(player, new RelocatePreviewResultPacket(packet.pick(), blocked), new CustomPacketPayload[0]);
            }
        });
    }

    private static boolean blockedByTrain(ServerPlayer player, RelocatePreviewPacket packet) {
        if (!packet.stored()) {
            return SableTrainSpawner.relocationBlockedByTrain(player, packet.subLevelId(), packet.pick(), packet.lookAngle());
        }

        ItemStack stack = player.getItemInHand(packet.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        CompoundTag data = RelocatorItemData.read(stack);
        return data != null
            && RelocatorItemData.isStored(data)
            && TrainStructures.blockedByTrain(player.serverLevel(), RelocatorItemData.structures(data), packet.pick(), packet.lookAngle());
    }
}
