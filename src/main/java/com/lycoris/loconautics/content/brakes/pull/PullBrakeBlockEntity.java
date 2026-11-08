package com.lycoris.loconautics.content.brakes.pull;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class PullBrakeBlockEntity extends SmartBlockEntity {
    public static final int MAX_POWER = 15;
    private static final double CHASE_SPEED = 0.35;
    public final LerpedFloat pull = LerpedFloat.linear().startWithValue(0.0);
    private static final int SOUND_COOLDOWN_TICKS = 6;
    private int power;
    private long lastSoundTick;
    private boolean synced;

    public PullBrakeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public int power() {
        return this.power;
    }

    public void setPower(int next) {
        int clamped = Mth.clamp(next, 0, 15);
        if (clamped != this.power && this.level != null) {
            this.power = clamped;
            if (this.level.isClientSide) {
                this.chase();
            } else {
                long now = this.level.getGameTime();
                if (now - this.lastSoundTick >= 6L) {
                    this.lastSoundTick = now;
                    this.level.playSound(null, this.worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.25F, 0.7F + this.power / 15.0F * 0.6F);
                }

                this.setChanged();
                this.notifyUpdate();
            }
        }
    }

    private void chase() {
        this.pull.chase(this.power / 15.0, 0.35, Chaser.EXP);
    }

    public void tick() {
        super.tick();
        if (this.level != null && this.level.isClientSide) {
            this.pull.tickChaser();
        }
    }

    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).inflate(0.0, 1.0, 0.0);
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Power", this.power);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.power = Mth.clamp(tag.getInt("Power"), 0, 15);
        if (clientPacket) {
            if (this.synced) {
                this.chase();
            } else {
                this.synced = true;
                this.pull.startWithValue(this.power / 15.0);
            }
        }
    }
}
