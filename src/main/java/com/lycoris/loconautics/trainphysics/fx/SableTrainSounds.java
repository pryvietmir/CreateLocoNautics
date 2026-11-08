package com.lycoris.loconautics.trainphysics.fx;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3d;
import org.joml.Vector3dc;

@OnlyIn(Dist.CLIENT)
public final class SableTrainSounds {
    private static final Map<UUID, SableTrainSounds> ACTIVE = new HashMap<>();
    private static final double SPEED_SCALE = 3.0;
    private final LerpedFloat speedFactor = LerpedFloat.linear();
    private final LerpedFloat distanceFactor = LerpedFloat.linear();
    private final LerpedFloat approachFactor = LerpedFloat.linear();
    private final float pitchModifier;
    private static final int SLIP_FADE_TICKS = 2;
    private static final double SLIP_PITCH_SCALE = 16.0;
    private SableTrainSounds.LoopingSound minecartEsqueSound;
    private SableTrainSounds.LoopingSound wheelSound;
    private SableTrainSounds.LoopingSound slipSound;
    private int slipFadeTicks;
    private float slipPitchJitter;
    private Vector3d lastPos;
    private int tick;
    private boolean wasDerailed;
    private int lastOffTrackBogeys;

    public static void tickAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stopAll();
        } else if (!mc.isPaused()) {
            Set<UUID> ids = SableTrainClientRegistry.ids();
            ACTIVE.entrySet().removeIf(e -> {
                if (!ids.contains(e.getKey())) {
                    e.getValue().stop();
                    return true;
                } else {
                    return false;
                }
            });
            if (!ids.isEmpty()) {
                SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
                if (container != null) {
                    for (UUID id : ids) {
                        SubLevel sub = container.getSubLevel(id);
                        if (sub instanceof ClientSubLevel clientSub && !sub.isRemoved()) {
                            ACTIVE.computeIfAbsent(id, k -> new SableTrainSounds(id)).tick(mc, clientSub);
                        } else {
                            SableTrainSounds sounds = ACTIVE.remove(id);
                            if (sounds != null) {
                                sounds.stop();
                            }
                        }
                    }
                }
            }
        }
    }

    public static void stopAll() {
        ACTIVE.values().forEach(SableTrainSounds::stop);
        ACTIVE.clear();
    }

    private SableTrainSounds(UUID id) {
        this.pitchModifier = Math.abs(id.hashCode()) * 10 % 13 / 36.0F;
        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(id);
        this.lastOffTrackBogeys = marker != null ? marker.offTrackBogeys() : 0;
        this.wasDerailed = marker != null && marker.derailed();
    }

    private void tick(Minecraft mc, ClientSubLevel sub) {
        Entity camEntity = mc.cameraEntity;
        if (camEntity != null) {
            Vector3dc p = sub.renderPose().position();
            Vector3d pos = new Vector3d(p);
            Vector3d motion = this.lastPos == null ? new Vector3d() : pos.sub(this.lastPos, new Vector3d());
            this.lastPos = pos;
            this.tick++;
            if (BogeyWheelAnimator.bogeysOf(sub).isEmpty()) {
                this.stopLoops();
            } else {
                SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
                double rawSpeed = marker != null ? Math.abs(marker.speed()) : motion.length();
                float effSpeed = (float)Math.min(rawSpeed * 3.0, 2.0);
                int offTrack = marker != null ? marker.offTrackBogeys() : 0;
                if (marker != null && marker.derailed()) {
                    if (!this.wasDerailed) {
                        this.wasDerailed = true;
                        this.stopLoops();
                        this.playCrash(mc, pos, marker.endOfTrack());
                    }

                    this.lastOffTrackBogeys = offTrack;
                    double sliding = motion.length();
                    if (sliding > 0.02 && this.tick % 3 == 0) {
                        float intensity = (float)Math.min(1.0, sliding * 3.0);
                        this.grindAtGroundedBogeys(mc, sub, intensity, 0.6F + intensity * 0.5F);
                    }
                } else {
                    this.wasDerailed = false;
                    if (offTrack > this.lastOffTrackBogeys) {
                        this.playCrash(mc, pos, marker != null && marker.endOfTrack());
                    }

                    this.lastOffTrackBogeys = offTrack;
                    if (marker != null && marker.brakeHard()) {
                        double sliding = motion.length();
                        if (sliding > 0.02 && this.tick % 6 == 0) {
                            float intensity = (float)Math.min(1.0, sliding * 3.0);
                            this.grindAtGroundedBogeys(mc, sub, intensity, 0.4F + intensity * 0.3F);
                        }
                    }

                    Vec3 cam = camEntity.getEyePosition();
                    Vec3 soundLocation = new Vec3(pos.x, pos.y, pos.z);
                    this.tickSlip(mc, marker, soundLocation);
                    Vec3 toTrain = soundLocation.subtract(cam);
                    double distance = toTrain.length();
                    Vec3 camMotion = camEntity.getDeltaMovement();
                    Vec3 combinedMotion = new Vec3(motion.x - camMotion.x, motion.y - camMotion.y, motion.z - camMotion.z);
                    double dot = !(distance < 0.001) && !(combinedMotion.lengthSqr() < 1.0E-9) ? toTrain.normalize().dot(combinedMotion.normalize()) : 0.0;
                    this.speedFactor.chase(effSpeed, 0.25, Chaser.exp(0.05F));
                    this.distanceFactor.chase(Mth.clampedLerp(100.0, 0.0, (distance - 3.0) / 64.0), 0.25, Chaser.exp(50.0));
                    this.approachFactor.chase(Mth.clampedLerp(50.0, 200.0, 0.5 * (dot + 1.0)), 0.25, Chaser.exp(10.0));
                    this.speedFactor.tickChaser();
                    this.distanceFactor.tickChaser();
                    this.approachFactor.tickChaser();
                    this.minecartEsqueSound = this.playIfMissing(mc, this.minecartEsqueSound, AllSoundEvents.TRAIN.getMainEvent());
                    this.wheelSound = this.playIfMissing(mc, this.wheelSound, AllSoundEvents.TRAIN2.getMainEvent());
                    float volume = Math.min(this.speedFactor.getValue(), this.approachFactor.getValue() / 300.0F + 0.0125F);
                    float pitch = Mth.clamp(this.speedFactor.getValue() * 2.0F + 0.25F, 0.75F, 1.95F) - this.pitchModifier;
                    this.minecartEsqueSound.setPitch(pitch * 1.5F);
                    volume = Math.min(volume, this.distanceFactor.getValue() / 1000.0F);
                    this.minecartEsqueSound.setLocation(soundLocation);
                    this.wheelSound.setLocation(soundLocation);
                    this.minecartEsqueSound.setVolume(volume / 2.0F);
                    float wheelVolume = Math.min(volume, Math.max((this.speedFactor.getValue() - 0.25F) / 4.0F + 0.01F, 0.0F));
                    this.wheelSound.setVolume(wheelVolume * 1.5F);
                }
            }
        }
    }

    private void playCrash(Minecraft mc, Vector3dc at, boolean endOfTrack) {
        if (!endOfTrack) {
            mc.level.playLocalSound(at.x(), at.y(), at.z(), SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.NEUTRAL, 0.4F, 0.85F, false);
            AllSoundEvents.STEAM.playAt(mc.level, new Vec3(at.x(), at.y(), at.z()), 0.25F, 0.8F, false);
        } else {
            mc.level.playLocalSound(at.x(), at.y(), at.z(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.0F, 0.55F, false);
            mc.level.playLocalSound(at.x(), at.y(), at.z(), SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.NEUTRAL, 1.0F, 0.7F, false);
            AllSoundEvents.STEAM.playAt(mc.level, new Vec3(at.x(), at.y(), at.z()), 1.0F, 0.6F, false);
        }
    }

    private void grindAtGroundedBogeys(Minecraft mc, ClientSubLevel sub, float intensity, float pitch) {
        for (AbstractBogeyBlockEntity be : BogeyWheelAnimator.bogeysOf(sub)) {
            Vec3 c = sub.renderPose().transformPosition(Vec3.atCenterOf(be.getBlockPos()));
            if (BogeyWheelAnimator.isGroundedAt(sub.getLevel(), c.x, c.y, c.z, BogeyWheelAnimator.railSide(be))) {
                mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL, intensity, pitch, false);
            }
        }
    }

    private void tickSlip(Minecraft mc, SableTrainClientRegistry.TrainMarker marker, Vec3 at) {
        if (marker != null && !marker.slippingBogeys().isEmpty()) {
            if (this.tick % 4 == 0) {
                this.slipPitchJitter = (mc.level.random.nextFloat() - 0.5F) * 0.04F;
            }

            this.slipFadeTicks = Math.min(this.slipFadeTicks + 1, 2);
            double spin = maxSlipSpeed(marker);
            float pitch = Mth.clamp(0.3F + (float)(spin * 16.0), 0.3F, 0.55F) + this.slipPitchJitter;
            this.slipSound = this.playIfMissing(mc, this.slipSound, SoundEvents.GRINDSTONE_USE);
            this.slipSound.setLocation(at);
            this.slipSound.setPitch(pitch);
            this.slipSound.setVolume(0.3F * (this.slipFadeTicks / 2.0F));
        } else {
            if (this.slipSound != null) {
                this.slipSound.stopSound();
                this.slipSound = null;
                this.slipFadeTicks = 0;
            }
        }
    }

    private static double maxSlipSpeed(SableTrainClientRegistry.TrainMarker marker) {
        double worst = 0.0;

        for (BlockPos pos : marker.slippingBogeys()) {
            Float rate = marker.drivenBogeys().get(pos);
            if (rate != null) {
                worst = Math.max(worst, Math.abs(rate));
            }
        }

        return worst;
    }

    private SableTrainSounds.LoopingSound playIfMissing(Minecraft mc, SableTrainSounds.LoopingSound sound, SoundEvent event) {
        if (sound == null) {
            sound = new SableTrainSounds.LoopingSound(event);
            mc.getSoundManager().play(sound);
        }

        return sound;
    }

    private void stopLoops() {
        if (this.minecartEsqueSound != null) {
            this.minecartEsqueSound.stopSound();
            this.minecartEsqueSound = null;
        }

        if (this.wheelSound != null) {
            this.wheelSound.stopSound();
            this.wheelSound = null;
        }

        if (this.slipSound != null) {
            this.slipSound.stopSound();
            this.slipSound = null;
            this.slipFadeTicks = 0;
        }
    }

    private void stop() {
        this.stopLoops();
        this.lastPos = null;
    }

    private static final class LoopingSound extends AbstractTickableSoundInstance {
        private LoopingSound(SoundEvent event) {
            super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.attenuation = Attenuation.LINEAR;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0E-4F;
        }

        public void tick() {
        }

        private void setVolume(float volume) {
            this.volume = volume;
        }

        private void setPitch(float pitch) {
            this.pitch = pitch;
        }

        private void setLocation(Vec3 location) {
            this.x = location.x;
            this.y = location.y;
            this.z = location.z;
        }

        private void stopSound() {
            Minecraft.getInstance().getSoundManager().stop(this);
        }
    }
}
