package com.lycoris.loconautics.content.smokestack;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.smoke.SmokeEmitter;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.lycoris.loconautics.foundation.smoke.SmokeStyle;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmokestackBlockEntity extends BlockEntity {
    private static final String COLOR_KEY = "Color";
    private static final String POWER_KEY = "Power";
    private static final float INTERVAL_BASE = 140.0F;
    private static final float LIFT_MIN = 0.7F;
    private static final float LIFT_MAX = 1.9F;
    private int color = 12106946;
    private int power;
    private static final int PUFF_PARTICLES = 6;
    private static final double PUFF_RADIUS = 0.2;
    public static final int BOILER_CLAIM_TIMEOUT = 60;
    private int chuffTimer;
    private int chuffJitter;
    private int boilerClaimAge;
    private boolean boilerDriven;
    private final SmokestackExhaust exhaust = new SmokestackExhaust();
    private final SmokeEmitter smoke = new SmokeEmitter();

    public SmokestackBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)LoconauticsRegistries.SMOKESTACK_BE.get(), pos, state);
    }

    public void setPower(int p) {
        p = Math.max(0, Math.min(15, p));
        if (p != this.power) {
            this.power = p;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    public int getPower() {
        return this.power;
    }

    public void setBoilerDriven(boolean driven) {
        if (driven) {
            this.boilerClaimAge = 0;
        }

        if (driven != this.boilerDriven) {
            this.boilerDriven = driven;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    public void serverTick() {
        if (this.boilerDriven) {
            if (++this.boilerClaimAge >= 60) {
                this.setBoilerDriven(false);
                if (this.level != null) {
                    SmokestackBlock.refreshColumn(this.level, this.worldPosition);
                }
            }
        }
    }

    public boolean isBoilerDriven() {
        return this.boilerDriven;
    }

    public void clientTick() {
        if (this.power <= 0) {
            this.chuffTimer = 0;
            this.smoke.stop();
            this.exhaust.reset();
        } else {
            this.smoke.track(new Vec3(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5));
            float strength = this.power / 15.0F;
            float lift = this.boilerDriven ? 1.0F : Mth.lerp(strength, 0.7F, 1.9F);
            float trailScale = 0.8F + 0.4F * strength;
            this.smoke
                .trail(
                    this.level, new SmokeOptions(this.color, SmokeStyle.TRAIL, trailScale, lift), particleRate() / Math.round(Mth.lerp(strength, 4.0F, 1.0F))
                );
            double wheelSpeed = this.boilerDriven ? SmokestackExhaust.wheelSpeed(this.worldPosition) : 0.0;
            if (this.exhaust.tick(this.level, this.smoke.position(), wheelSpeed, this.power)) {
                this.chuffTimer = 0;
                this.puff(strength, SmokestackExhaust.pace(wheelSpeed), lift);
            } else if (!SmokestackExhaust.isMoving(wheelSpeed)) {
                int interval = Math.max(2, Math.round(140.0F / this.power)) + this.chuffJitter;
                if (++this.chuffTimer >= interval) {
                    this.chuffTimer = 0;
                    this.chuffJitter = this.level.random.nextInt(5) - 2;
                    if (this.boilerDriven) {
                        this.exhaust.idleChuff(this.level, this.smoke.position(), this.power);
                    }

                    this.puff(strength, 0.0F, lift);
                }
            }
        }
    }

    private void puff(float strength, float pace, float lift) {
        float scale = (0.9F + 0.4F * strength) * (1.1F - 0.25F * pace);
        this.smoke.puff(this.level, new SmokeOptions(this.color, SmokeStyle.CHUFF, scale, lift), Math.max(1, Math.round(6.0F * particleRate())), 0.2);
    }

    private static float particleRate() {
        return ((Double)LoconauticsConfig.SMOKESTACK_PARTICLES_PER_TICK.get()).floatValue();
    }

    public int getColor() {
        return this.color;
    }

    public void setColor(int rgb) {
        int next = rgb & 16777215;
        if (next != this.color) {
            this.color = next;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    protected void saveAdditional(CompoundTag tag, Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Color", this.color);
        tag.putInt("Power", this.power);
        tag.putBoolean("BoilerDriven", this.boilerDriven);
    }

    protected void loadAdditional(CompoundTag tag, Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Color")) {
            this.color = tag.getInt("Color") & 16777215;
        }

        if (tag.contains("Power")) {
            this.power = tag.getInt("Power");
        }

        this.boilerDriven = tag.getBoolean("BoilerDriven");
    }

    public CompoundTag getUpdateTag(Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Color", this.color);
        tag.putInt("Power", this.power);
        tag.putBoolean("BoilerDriven", this.boilerDriven);
        return tag;
    }

    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            this.loadAdditional(tag, registries);
        }
    }
}
