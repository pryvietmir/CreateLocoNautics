package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.compat.create.psi.SablePsiDriver;
import com.lycoris.loconautics.compat.create.psi.SablePsiFrames;
import com.lycoris.loconautics.compat.create.psi.SablePsiHost;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.UUID;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PortableStorageInterfaceBlockEntity.class, remap = false)
public abstract class PortableStorageInterfaceSableMixin extends SmartBlockEntity implements SablePsiHost {
    @Shadow
    protected float distance;
    @Shadow
    protected LerpedFloat connectionAnimation;
    @Unique
    private UUID loconautics$partnerSubLevel;
    @Unique
    private BlockPos loconautics$partnerPos;
    @Unique
    private boolean loconautics$linked;

    private PortableStorageInterfaceSableMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void loconautics$driveSableLink(CallbackInfo ci) {
        SablePsiDriver.tick((PortableStorageInterfaceBlockEntity)(Object)this);
    }

    @Inject(method = "stopTransferring", at = @At("TAIL"))
    private void loconautics$dropSableLink(CallbackInfo ci) {
        SablePsiDriver.unlink((PortableStorageInterfaceBlockEntity)(Object)this);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void loconautics$writeLink(CompoundTag tag, Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (clientPacket) {
            tag.putBoolean("LoconauticsLinked", this.loconautics$linked && this.loconautics$partnerPos != null);
            if (this.loconautics$partnerPos != null) {
                tag.putLong("LoconauticsPartner", this.loconautics$partnerPos.asLong());
            }
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void loconautics$readLink(CompoundTag tag, Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (clientPacket) {
            boolean linked = tag.getBoolean("LoconauticsLinked");
            this.loconautics$linked = linked;
            this.loconautics$partnerPos = linked ? BlockPos.of(tag.getLong("LoconauticsPartner")) : null;
        }
    }

    @Inject(method = "getExtensionDistance", at = @At("HEAD"), cancellable = true)
    private void loconautics$liveExtension(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (this.loconautics$linked && this.loconautics$partnerPos != null && this.level != null && this.level.isClientSide) {
            Float reach = SablePsiFrames.liveReach((PortableStorageInterfaceBlockEntity)(Object)this, this.loconautics$partnerPos);
            if (reach != null) {
                double eased = Math.pow(this.connectionAnimation.getValue(partialTicks), 2.0);
                cir.setReturnValue((float)(eased * reach.floatValue() / 2.0));
            }
        }
    }

    @Override
    public void loconautics$link(@Nullable UUID partnerSubLevel, BlockPos partnerPos) {
        this.loconautics$partnerSubLevel = partnerSubLevel;
        this.loconautics$partnerPos = partnerPos;
        this.loconautics$linked = true;
    }

    @Override
    public void loconautics$unlink() {
        this.loconautics$partnerSubLevel = null;
        this.loconautics$partnerPos = null;
        this.loconautics$linked = false;
    }

    @Override
    public boolean loconautics$linked() {
        return this.loconautics$linked;
    }

    @Nullable
    @Override
    public UUID loconautics$partnerSubLevel() {
        return this.loconautics$partnerSubLevel;
    }

    @Nullable
    @Override
    public BlockPos loconautics$partnerPos() {
        return this.loconautics$partnerPos;
    }

    @Override
    public void loconautics$setDistance(float value) {
        this.distance = value;
    }

    @Override
    public float loconautics$distance() {
        return this.distance;
    }
}
