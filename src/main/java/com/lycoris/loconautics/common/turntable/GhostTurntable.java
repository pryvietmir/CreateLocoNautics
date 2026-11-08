package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class GhostTurntable implements TurntableMotion.Host {
    private static final int ORPHAN_TICKS = 100;
    private final UUID plate;
    private final ResourceKey<Level> dimension;
    private final BlockPos bearingPos;
    private final TurntableMotion motion = new TurntableMotion();
    private final TurntableSignalHold signals = new TurntableSignalHold();
    private boolean powered;
    private float angularPerTick;
    private boolean facingNegative;
    private float speedOverride;
    private boolean neighborSignal;
    private int orphanTicks;

    public GhostTurntable(UUID plate, ResourceKey<Level> dimension, BlockPos bearingPos) {
        this.plate = plate;
        this.dimension = dimension;
        this.bearingPos = bearingPos;
    }

    public void snapshot(boolean powered, float angularPerTick, boolean facingNegative, float speedOverride, boolean neighborSignal) {
        this.powered = powered;
        this.angularPerTick = angularPerTick;
        this.facingNegative = facingNegative;
        this.speedOverride = speedOverride;
        this.neighborSignal = neighborSignal;
    }

    public TurntableMotion motion() {
        return this.motion;
    }

    public TurntableSignalHold signals() {
        return this.signals;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public BlockPos bearingPos() {
        return this.bearingPos;
    }

    @Nullable
    public TrackHostFrames.Frame frame() {
        return TrackHostFrames.frameOf(this.plate);
    }

    public void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(this.dimension);
        if (level != null) {
            this.signals.tick(level, this.motion.busy(this.plate));
            if (!ExternalForceDerail.staffLockedById(level, List.of(this.plate))) {
                this.motion.beginTick();
                this.motion.step(this, this.motion.scheduleDriven());
                TrackHostFrames.setAngle(this.plate, this.motion.targetAngleDegrees());
                this.trace(level);
            }
        }
    }

    private void trace(ServerLevel level) {
        if (TurntableTrace.due("ghost" + this.plate, level.getGameTime(), 20)) {
            TrackHostFrames.Frame frame = this.frame();
            TurntableTrace.log(
                "ghost plate "
                    + LogFormat.shortId(this.plate)
                    + " target="
                    + String.format("%.2f", this.motion.targetAngleDegrees())
                    + " perTick="
                    + this.angularPerTick
                    + " powered="
                    + this.powered
                    + " sched="
                    + this.motion.scheduleDriven()
                    + " alignTarget="
                    + (this.motion.alignTarget() != null)
                    + " aligned="
                    + this.motion.aligned()
                    + " settled="
                    + this.motion.settled()
                    + " reserved="
                    + TurntableRegistry.reserved(this.plate)
                    + " occupied="
                    + TurntableRegistry.occupied(this.plate)
                    + " registered="
                    + (TurntableRegistry.byPlate(this.plate) == this)
                    + " frameLoaded="
                    + (frame != null && frame.loaded())
                    + " bearingLoaded="
                    + level.isLoaded(this.bearingPos)
                    + " orphanTicks="
                    + this.orphanTicks
            );
        }
    }

    boolean orphaned(MinecraftServer server) {
        ServerLevel level = server.getLevel(this.dimension);
        if (level == null) {
            return false;
        }

        boolean bearingGone = level.isLoaded(this.bearingPos) && !(level.getBlockEntity(this.bearingPos) instanceof TurntableGhosts.Source);
        this.orphanTicks = this.frame() != null && !bearingGone ? 0 : this.orphanTicks + 1;
        return this.orphanTicks > 100;
    }

    @Override
    public UUID plateSubLevelId() {
        return this.plate;
    }

    @Nullable
    @Override
    public Vec3 turntableAxisWorld() {
        TrackHostFrames.Frame frame = this.frame();
        return frame == null ? null : frame.axis();
    }

    @Nullable
    @Override
    public Vec3 turntableAxisNormal() {
        TrackHostFrames.Frame frame = this.frame();
        return frame == null ? null : frame.normal();
    }

    @Override
    public boolean turntablePowered() {
        return this.powered;
    }

    @Override
    public boolean turntableScheduleDriven() {
        return this.motion.scheduleDriven();
    }

    @Override
    public void turntableAlignTo(TrackNodeLocation movingHead, TrackNodeLocation target) {
        this.motion.alignTo(movingHead, target);
    }

    @Override
    public void turntableClearAlignment() {
        this.motion.clearAlignment();
    }

    @Nullable
    @Override
    public TrackNodeLocation turntableAlignTarget() {
        return this.motion.alignTarget();
    }

    @Override
    public boolean turntableAligned() {
        return this.motion.aligned();
    }

    @Override
    public boolean turntableSettled() {
        return this.motion.settled();
    }

    @Override
    public float angularPerTick() {
        return this.angularPerTick;
    }

    @Override
    public boolean facingNegative() {
        return this.facingNegative;
    }

    @Override
    public float speedOverride() {
        return this.speedOverride;
    }

    @Override
    public boolean neighborSignal() {
        return this.neighborSignal;
    }

    @Override
    public void motionChanged() {
        TurntableGhosts.markDirty();
    }

    @Override
    public void bodiesMoved() {
        TurntableGhosts.markDirty();
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Plate", this.plate);
        tag.putString("Dimension", this.dimension.location().toString());
        tag.put("BearingPos", NbtUtils.writeBlockPos(this.bearingPos));
        tag.putBoolean("Powered", this.powered);
        tag.putFloat("AngularPerTick", this.angularPerTick);
        tag.putBoolean("FacingNegative", this.facingNegative);
        tag.putFloat("SpeedOverride", this.speedOverride);
        tag.putBoolean("NeighborSignal", this.neighborSignal);
        CompoundTag motionTag = new CompoundTag();
        this.motion.write(motionTag);
        tag.put("Motion", motionTag);
        this.signals.write(tag);
        return tag;
    }

    @Nullable
    static GhostTurntable read(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        BlockPos pos = (BlockPos)NbtUtils.readBlockPos(tag, "BearingPos").orElse(null);
        if (tag.hasUUID("Plate") && dimension != null && pos != null) {
            GhostTurntable ghost = new GhostTurntable(tag.getUUID("Plate"), ResourceKey.create(Registries.DIMENSION, dimension), pos);
            ghost.snapshot(
                tag.getBoolean("Powered"),
                tag.getFloat("AngularPerTick"),
                tag.getBoolean("FacingNegative"),
                tag.getFloat("SpeedOverride"),
                tag.getBoolean("NeighborSignal")
            );
            ghost.motion.read(tag.getCompound("Motion"));
            ghost.signals.read(tag);
            return ghost;
        } else {
            return null;
        }
    }
}
