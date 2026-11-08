package com.lycoris.loconautics.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import foundry.veil.api.network.VeilPacketManager.PacketSink;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SubLevelTrackingSystem.class, remap = false)
public abstract class SubLevelTrackingLevelMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lfoundry/veil/api/network/VeilPacketManager;player(Lnet/minecraft/server/level/ServerPlayer;)Lfoundry/veil/api/network/VeilPacketManager$PacketSink;"
        )
    )
    private PacketSink loconautics$removalOnlyInThisLevel(ServerPlayer player, Operation<PacketSink> original) {
        PacketSink sink = (PacketSink)original.call(new Object[]{player});
        return packet -> this.loconautics$send(player, sink, packet);
    }

    @Inject(method = "serverWidePlayerSink", at = @At("HEAD"), cancellable = true)
    private void loconautics$trackersInThisLevel(ServerSubLevel subLevel, CallbackInfoReturnable<PacketSink> cir) {
        cir.setReturnValue((PacketSink)packet -> {
            for (UUID uuid : subLevel.getTrackingPlayers()) {
                ServerPlayer player = this.level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    this.loconautics$send(player, player.connection::send, packet);
                }
            }
        });
    }

    @Unique
    private void loconautics$send(ServerPlayer player, PacketSink sink, Packet<?> packet) {
        if (player.serverLevel() == this.level) {
            sink.sendPacket(packet);
        } else if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof ClientboundStopTrackingSubLevelPacket stop) {
            int var12 = ChunkPos.getX(stop.plotCoordinate());
            int z = ChunkPos.getZ(stop.plotCoordinate());
            SubLevelContainer there = SubLevelContainer.getContainer(player.serverLevel());
            SubLevel victim = there == null ? null : there.getSubLevel(var12, z);
            if (victim != null) {
                SubLevelContainer here = SubLevelContainer.getContainer(this.level);
                if (here == null) {
                    SubLevel source = null;
                } else {
                    here.getSubLevel(var12, z);
                }
            }
        }
    }
}
