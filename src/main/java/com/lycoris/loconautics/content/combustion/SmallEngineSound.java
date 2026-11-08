package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.registry.LoconauticsSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class SmallEngineSound extends AbstractTickableSoundInstance {
    private static final float FULL_VOLUME = 0.8F;
    private static final float QUIET_SHARE = 0.25F;
    private static final float SLOW_PITCH = 0.55F;
    private static final float FULL_PITCH = 1.05F;
    private static final float FADE_BELOW = 0.12F;
    private static final int LOUD_ENGINES = 6;
    private static final double CROWD_RANGE = 24.0;
    private static long countedAt = -1L;
    private static int counting;
    private static int crowd;
    private final SmallEngineBlockEntity engine;
    private float spin;

    public SmallEngineSound(SmallEngineBlockEntity engine) {
        super((SoundEvent)LoconauticsSounds.SMALL_ENGINE_LOOP.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.engine = engine;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0E-4F;
        this.x = engine.getBlockPos().getX() + 0.5;
        this.y = engine.getBlockPos().getY() + 0.5;
        this.z = engine.getBlockPos().getZ() + 0.5;
    }

    public void tick() {
        if (this.engine.isRemoved()) {
            this.stop();
        } else {
            this.spin = this.engine.getShownSpin();
            if (!this.engine.isRunning() && this.spin <= 0.002F) {
                this.stop();
            } else {
                this.pitch = Mth.lerp(this.spin, 0.55F, 1.05F);
                this.volume = 0.8F
                    * Mth.lerp(this.spin, 0.25F, 1.0F)
                    * Mth.clamp(this.spin / 0.12F, 0.0F, 1.0F)
                    * crowdShare(this.engine.getLevel().getGameTime(), this.engine.getBlockPos());
            }
        }
    }

    private static float crowdShare(long gameTime, BlockPos at) {
        if (gameTime != countedAt) {
            countedAt = gameTime;
            crowd = counting;
            counting = 0;
        }

        if (Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(Vec3.atCenterOf(at)) <= 576.0) {
            counting++;
        }

        return crowd <= 6 ? 1.0F : 6.0F / crowd;
    }
}
