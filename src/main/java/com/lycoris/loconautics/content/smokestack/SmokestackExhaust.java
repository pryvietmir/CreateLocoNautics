package com.lycoris.loconautics.content.smokestack;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.AllSoundEvents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SmokestackExhaust {
    private static final double DRIVER_DIAMETER = 1.5625;
    private static final double BEATS_PER_BLOCK = 0.8148733086305041;
    private static final int MIN_BEAT_TICKS = 2;
    private static final double MOVING_SPEED = 0.01;
    private static final double FULL_SPEED = 0.6;
    private static final float CHUFF_VOLUME = 0.2F;
    private static final float IDLE_CHUFF_VOLUME = 0.25F;
    private static final float RELEASE_VOLUME = 0.4F;
    private static final float MAX_STEAM_SPENT = 10.0F;
    private double phase;
    private int beat;
    private int ticksSinceBeat;
    private float steamSpent;

    public static double wheelSpeed(BlockPos pos) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(pos);
        if (sub == null) {
            return 0.0;
        }

        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
        if (marker != null && !marker.derailed() && !marker.brakeHard()) {
            double speed = Math.abs(marker.speed());

            for (float driven : marker.drivenBogeys().values()) {
                speed = Math.max(speed, Math.abs(driven));
            }

            return speed;
        } else {
            return 0.0;
        }
    }

    public static float pace(double wheelSpeed) {
        return (float)Mth.clamp(wheelSpeed / 0.6, 0.0, 1.0);
    }

    public static boolean isMoving(double wheelSpeed) {
        return wheelSpeed >= 0.01;
    }

    public boolean tick(Level level, Vec3 at, double wheelSpeed, int firePower) {
        this.ticksSinceBeat++;
        if (!isMoving(wheelSpeed)) {
            this.release(level, at);
            this.phase = 0.0;
            return false;
        } else {
            this.steamSpent = Math.min(this.steamSpent + (float)Math.min(0.5, wheelSpeed / 10.0), 10.0F);
            this.phase = Math.min(this.phase + wheelSpeed * 0.8148733086305041, 1.0);
            if (!(this.phase < 1.0) && this.ticksSinceBeat >= 2) {
                this.phase--;
                this.ticksSinceBeat = 0;
                this.chuff(level, at, wheelSpeed, firePower);
                return true;
            } else {
                return false;
            }
        }
    }

    public void idleChuff(Level level, Vec3 at, int firePower) {
        float fire = 0.35F + 0.65F * firePower / 15.0F;
        float pitch = 0.95F + (level.random.nextFloat() - 0.5F) * 0.1F;
        AllSoundEvents.STEAM.playAt(level, at, 0.25F * fire, pitch, false);
    }

    public void reset() {
        this.phase = 0.0;
        this.steamSpent = 0.0F;
    }

    private void chuff(Level level, Vec3 at, double wheelSpeed, int firePower) {
        float pace = pace(wheelSpeed);
        float fire = 0.35F + 0.65F * firePower / 15.0F;
        boolean downbeat = this.beat % 4 == 0;
        float accent = downbeat ? 1.5F : (this.beat % 2 == 0 ? 0.75F : 0.45F);
        float volume = 0.2F * accent * fire * (0.6F + 0.4F * pace);
        float pitch = (downbeat ? 0.8F : 1.17F) * (0.9F + 0.2F * pace) + (level.random.nextFloat() - 0.5F) * 0.06F;
        AllSoundEvents.STEAM.playAt(level, at, volume, pitch, false);
        this.beat++;
    }

    private void release(Level level, Vec3 at) {
        if (this.steamSpent > 1.0F) {
            float volume = this.steamSpent / 10.0F * 0.4F;
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 0.25F * volume, 0.78F, false);
            AllSoundEvents.STEAM.playAt(level, at, 0.75F * volume, 0.5F, false);
        }

        this.steamSpent = 0.0F;
    }
}
