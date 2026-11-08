package com.lycoris.loconautics.content.boiler.safetyvalve;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.lycoris.loconautics.foundation.smoke.SmokeStyle;
import com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock;
import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public abstract class SafetyValveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final ResourceKey<DamageType> SCALD = ResourceKey.create(Registries.DAMAGE_TYPE, LoconauticsConstants.id("safety_valve"));
    private static final float SCALD_DAMAGE = 1.0F;
    private static final int STEAM_COLOR = 15922679;
    private float opening;
    private boolean venting;
    private int hissCooldown;

    public SafetyValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void tick() {
        super.tick();
        if (this.level != null && this.venting && !(this.opening <= 0.0F)) {
            if (this.level.isClientSide) {
                this.ventSteam();
            } else {
                this.scaldEntities();
            }
        }
    }

    protected Vec3 mouthDirection() {
        return new Vec3(0.0, 1.0, 0.0);
    }

    protected double mouthDistance() {
        return 0.5;
    }

    private Vec3 localMouth() {
        return Vec3.atCenterOf(this.worldPosition).add(this.mouthDirection().scale(this.mouthDistance()));
    }

    private void scaldEntities() {
        Vec3 localMouth = this.localMouth();
        Vec3 worldMouth = Sable.HELPER.projectOutOfSubLevel(this.level, localMouth);
        Vec3 worldUp = Sable.HELPER.projectOutOfSubLevel(this.level, localMouth.add(this.mouthDirection())).subtract(worldMouth);
        Vec3 tip = worldMouth.add(worldUp.scale(0.5 + 1.5 * this.opening));
        AABB steam = new AABB(worldMouth, tip).inflate(0.3);

        for (LivingEntity entity : this.level.getEntitiesOfClass(LivingEntity.class, steam)) {
            entity.igniteForSeconds(3.0F);
            entity.hurt(this.level.damageSources().source(SCALD), 1.0F);
        }
    }

    private void ventSteam() {
        RandomSource random = this.level.random;
        Vec3 localMouth = this.localMouth();
        if (!PortalCutsClient.hiddenAtPlot(localMouth.x, localMouth.y, localMouth.z)) {
            Vec3 worldMouth = Sable.HELPER.projectOutOfSubLevel(this.level, localMouth);
            Vec3 worldUp = Sable.HELPER.projectOutOfSubLevel(this.level, localMouth.add(this.mouthDirection())).subtract(worldMouth);
            float perTick = 0.8F + this.opening * 2.2F;
            int count = (int)perTick + (random.nextFloat() < perTick - (int)perTick ? 1 : 0);
            double speed = 0.18 + 0.42 * this.opening;
            float scale = 0.8F + 0.7F * this.opening;
            Vec3 spread = new Vec3(1.0 - Math.abs(worldUp.x), 1.0 - Math.abs(worldUp.y), 1.0 - Math.abs(worldUp.z));

            for (int i = 0; i < count; i++) {
                ParticleWorldLock.addParticle(
                    this.level,
                    new SmokeOptions(15922679, SmokeStyle.STEAM, scale),
                    worldMouth.x + (random.nextDouble() - 0.5) * 0.25 * spread.x,
                    worldMouth.y + (random.nextDouble() - 0.5) * 0.25 * spread.y,
                    worldMouth.z + (random.nextDouble() - 0.5) * 0.25 * spread.z,
                    worldUp.x * speed + (random.nextDouble() - 0.5) * 0.05 * spread.x,
                    worldUp.y * speed + (random.nextDouble() - 0.5) * 0.05 * spread.y,
                    worldUp.z * speed + (random.nextDouble() - 0.5) * 0.05 * spread.z
                );
            }

            if (--this.hissCooldown <= 0) {
                AllSoundEvents.STEAM.playAt(this.level, worldMouth, 0.3F + 0.7F * this.opening, 0.7F + 0.5F * this.opening, false);
                this.hissCooldown = 4 + Math.round((1.0F - this.opening) * 8.0F);
            }
        }
    }

    public float opening() {
        return this.opening;
    }

    public void setVenting(boolean value) {
        if (this.venting != value) {
            this.venting = value;
            this.setChanged();
            this.sendData();
        }
    }

    public void setOpening(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        if (!(Math.abs(clamped - this.opening) < 0.001F)) {
            this.opening = clamped;
            this.setChanged();
            this.sendData();
        }
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.safety_valve_stats").forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.safety_valve_opening").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder()
            .text(Mth.floor(this.opening * 100.0F) + "%")
            .style(this.opening <= 0.0F ? ChatFormatting.RED : ChatFormatting.GOLD)
            .forGoggles(tooltip, 1);
        return true;
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Opening", this.opening);
        tag.putBoolean("Venting", this.venting);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.opening = tag.getFloat("Opening");
        this.venting = tag.getBoolean("Venting");
    }
}
