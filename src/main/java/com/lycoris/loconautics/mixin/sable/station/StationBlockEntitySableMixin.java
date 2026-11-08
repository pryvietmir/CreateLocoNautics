package com.lycoris.loconautics.mixin.sable.station;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.network.packets.train.StationParkedSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.StationLastStyle;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StationBlockEntity.class, remap = false)
public abstract class StationBlockEntitySableMixin extends SmartBlockEntity implements StationLastStyle {
    @Unique
    private boolean loconautics$parked;
    @Unique
    private String loconautics$name = "";
    @Unique
    private List<Integer> loconautics$carriages = List.of();
    @Unique
    private int loconautics$mapColor;
    @Unique
    private String loconautics$icon = "";
    @Unique
    private String loconautics$lastIcon = "";
    @Unique
    private int loconautics$refreshTick;
    @Unique
    private float loconautics$approach = Float.MAX_VALUE;
    @Unique
    private static final double loconautics$APPROACH_RANGE = 30.0;

    private StationBlockEntitySableMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow
    public abstract GlobalStation getStation();

    @Shadow
    public abstract boolean isAssembling();

    @Override
    public String loconautics$getLastIcon() {
        return this.loconautics$lastIcon;
    }

    @Override
    public void loconautics$setLastIcon(String icon) {
        this.loconautics$lastIcon = icon == null ? "" : icon;
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void loconautics$readLastIcon(CompoundTag tag, Provider registries, boolean clientPacket, CallbackInfo ci) {
        this.loconautics$lastIcon = tag.getString("LoconauticsPrevTrainIcon");
    }

    @Inject(method = "write", at = @At("HEAD"))
    private void loconautics$writeLastIcon(CompoundTag tag, Provider registries, boolean clientPacket, CallbackInfo ci) {
        tag.putString("LoconauticsPrevTrainIcon", this.loconautics$lastIcon);
    }

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    private void loconautics$blockCreateAssembly(UUID playerUUID, CallbackInfo ci) {
        if (!(Boolean)LoconauticsConfig.ENABLE_CREATE_TRAINS.get()) {
            ((StationBlockEntityAccessor)this)
                .loconautics$exception(new AssemblyException(Component.translatable("loconautics.station.createtrain.disabled")), -1);
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void loconautics$detectParkedSableTrain(CallbackInfo ci) {
        if (this.getLevel() instanceof ServerLevel serverLevel) {
            boolean parked = false;
            String name = "";
            List carriages = List.of();
            int mapColor = 0;
            String icon = "";
            float approach = Float.MAX_VALUE;
            GlobalStation station = this.getStation();
            if (!this.isAssembling() && station != null) {
                SableTrain train = SableTrainSpawner.findParkedTrain(serverLevel, station);
                if (train != null) {
                    parked = true;
                    approach = 0.0F;
                } else {
                    SableTrain inbound = SableTrainSpawner.findApproachingTrain(serverLevel, station);
                    double gap = SableTrainSpawner.approachDistance(inbound, station);
                    if (gap < Double.MAX_VALUE && gap <= 30.0) {
                        train = ConsistNose.of(inbound).car();
                        approach = (float)Math.max(0.01, gap);
                    }
                }

                if (train != null) {
                    name = SableTrainSpawner.trainName(serverLevel, train);
                    carriages = SableTrainSpawner.consistCarriageIcons(serverLevel, train);
                    mapColor = train.mapColorIndex();
                    icon = train.iconType();
                }
            }

            if (approach != this.loconautics$approach
                || parked != this.loconautics$parked
                || !name.equals(this.loconautics$name)
                || !carriages.equals(this.loconautics$carriages)
                || mapColor != this.loconautics$mapColor
                || !icon.equals(this.loconautics$icon)
                || approach != this.loconautics$approach) {
                this.loconautics$parked = parked;
                this.loconautics$name = name;
                this.loconautics$carriages = carriages;
                this.loconautics$mapColor = mapColor;
                this.loconautics$icon = icon;
                this.loconautics$approach = approach;
                StationParkedSyncPacket.broadcast(this.getBlockPos(), parked, name, carriages, mapColor, icon, approach);
            } else if (parked && this.loconautics$refreshTick % 20 == 0) {
                StationParkedSyncPacket.broadcast(this.getBlockPos(), true, name, carriages, mapColor, icon, 0.0F);
            }

            this.loconautics$refreshTick++;
        }
    }
}
