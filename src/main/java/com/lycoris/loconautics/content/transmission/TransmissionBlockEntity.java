package com.lycoris.loconautics.content.transmission;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.config.command.ConfigWalker;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.debug.TransmissionTrace;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TransmissionBlockEntity extends SplitShaftBlockEntity implements MenuProvider, LinkHolder {
    private final RedstoneLinkPair links = new RedstoneLinkPair(this, this::setRedstonePower, s -> this.setDirectionActive(s > 0));
    private int redstonePower;
    private boolean directionActive;
    private boolean kineticRefreshQueued;
    private boolean reattachPending;
    private Float computerRpm;
    private Boolean computerReversed;

    public TransmissionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void initialize() {
        super.initialize();
        if (this.level != null && !this.level.isClientSide) {
            TransmissionTrace.note(this, "initialize before links");
            this.links.register(this.level);
            TransmissionTrace.note(this, "initialize after links");
        }
    }

    @Override
    public void releaseLinks() {
        if (this.level != null) {
            this.links.unregister(this.level);
        }
    }

    public void invalidate() {
        TransmissionTrace.removed(this);
        if (this.level != null) {
            this.links.unregister(this.level);
        }

        super.invalidate();
    }

    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.level != null) {
            this.links.unregister(this.level);
        }
    }

    public float getRotationSpeedModifier(Direction face) {
        if (!this.hasSource()) {
            return 1.0F;
        }

        if (face == this.getSourceFacing()) {
            return 1.0F;
        }

        float inputSpeed = this.getTheoreticalSpeed();
        if (inputSpeed == 0.0F) {
            return 0.0F;
        }

        float targetRPM = this.computeTargetRPM();
        if (targetRPM == 0.0F) {
            return 0.0F;
        }

        float sign = this.isReversed() ? -1.0F : 1.0F;
        return sign * targetRPM / Math.abs(inputSpeed);
    }

    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (this.level != null && !this.level.isClientSide && this.hasNetwork()) {
            this.getOrCreateNetwork().updateStressFor(this, this.calculateStressApplied());
        }
    }

    public float calculateStressApplied() {
        float impact = ConfigWalker.getOrDefault(() -> ((Double)LoconauticsConfig.TRANSMISSION_SU_IMPACT.get()).floatValue(), 4.0F, "transmission_su_impact");
        float inputSpeed = Math.abs(this.getTheoreticalSpeed());
        return inputSpeed == 0.0F ? impact : impact * this.computeTargetRPM() / inputSpeed;
    }

    public float calculateAddedStressCapacity() {
        return 0.0F;
    }

    private float computeTargetRPM() {
        if (this.computerRpm != null) {
            return this.computerRpm;
        } else {
            return this.redstonePower == 0 ? 0.0F : (float)Math.ceil(this.redstonePower * 256.0 / 15.0);
        }
    }

    public int getPower() {
        return this.computerRpm != null ? Math.round(this.computerRpm * 15.0F / 256.0F) : this.redstonePower;
    }

    public float getTargetRpm() {
        return this.computeTargetRPM();
    }

    public boolean isReversed() {
        return this.computerReversed != null ? this.computerReversed : this.directionActive;
    }

    public boolean isComputerDriven() {
        return this.computerRpm != null;
    }

    public float getComputerRpm() {
        return this.computerRpm == null ? 0.0F : this.computerRpm;
    }

    public boolean isComputerReversed() {
        return this.computerReversed != null && this.computerReversed;
    }

    public void setComputerDrive(float rpm, boolean reversed) {
        float clamped = Math.max(0.0F, Math.min(256.0F, rpm));
        if (this.computerRpm == null || this.computerRpm != clamped || this.computerReversed != reversed) {
            this.computerRpm = clamped;
            this.computerReversed = reversed;
            this.kineticRefreshQueued = true;
        }
    }

    public void clearComputerDrive() {
        if (this.computerRpm != null) {
            this.computerRpm = null;
            this.computerReversed = null;
            this.kineticRefreshQueued = true;
        }
    }

    public void setRedstonePower(int power) {
        int clamped = Math.max(0, Math.min(15, power));
        TransmissionTrace.note(this, "setRedstonePower " + this.redstonePower + " to " + clamped);
        if (this.redstonePower != clamped) {
            this.redstonePower = clamped;
            this.kineticRefreshQueued = true;
            this.setChanged();
            this.sendData();
        }
    }

    public void setDirectionActive(boolean active) {
        TransmissionTrace.note(this, "setDirectionActive " + this.directionActive + " to " + active);
        if (this.directionActive != active) {
            this.directionActive = active;
            this.kineticRefreshQueued = true;
            this.setChanged();
            this.sendData();
        }
    }

    public void tick() {
        if (this.reattachPending && this.level != null && !this.level.isClientSide) {
            this.reattachPending = false;
            TransmissionTrace.note(this, "reattach");
            RotationPropagator.handleAdded(this.level, this.worldPosition, this);
        }

        super.tick();
        if (this.level != null && !this.level.isClientSide && this.kineticRefreshQueued) {
            this.kineticRefreshQueued = false;
            this.applyKineticState();
        }
    }

    private void applyKineticState() {
        BlockState state = this.getBlockState();
        if (state.getBlock() instanceof TransmissionBlock block) {
            TransmissionTrace.note(this, "apply detach");
            block.detachKinetics(this.level, this.worldPosition);
            this.reattachPending = true;
            this.setChanged();
            BlockState updated = (BlockState)state.setValue(TransmissionBlock.POWERED, this.computeTargetRPM() > 0.0F);
            if (!updated.equals(state)) {
                this.level.setBlock(this.worldPosition, updated, 2);
            }
        }
    }

    public void setSpeedFrequency(ItemStack first, ItemStack second) {
        this.links.setFirstFrequency(first, second);
        if (this.level != null && !this.level.isClientSide) {
            this.links.register(this.level);
        }

        this.setChanged();
        this.sendData();
    }

    public void setDirectionFrequency(ItemStack first, ItemStack second) {
        this.links.setSecondFrequency(first, second);
        if (this.level != null && !this.level.isClientSide) {
            this.links.register(this.level);
        }

        this.setChanged();
        this.sendData();
    }

    public ItemStack getSpeedFreqFirst() {
        return this.links.getFirstFreqA();
    }

    public ItemStack getSpeedFreqSecond() {
        return this.links.getFirstFreqB();
    }

    public ItemStack getDirFreqFirst() {
        return this.links.getSecondFreqA();
    }

    public ItemStack getDirFreqSecond() {
        return this.links.getSecondFreqB();
    }

    public Component getDisplayName() {
        return Component.translatable("block.loconautics.transmission");
    }

    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TransmissionMenu((MenuType<?>)LoconauticsRegistries.TRANSMISSION_MENU.get(), id, inventory, this);
    }

    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.translatable("gui.loconautics.bind_frequency.speed"));
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.translatable("gui.loconautics.bind_frequency.direction"));
        super.sendToMenu(buffer);
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("RedstonePower", this.redstonePower);
        tag.putBoolean("DirectionActive", this.directionActive);
        if (!clientPacket && this.reattachPending) {
            tag.putBoolean("ReattachPending", true);
        }

        CompoundTag freq = new CompoundTag();
        freq.put("SpeedFirst", this.links.getFirstFreqA().saveOptional(registries));
        freq.put("SpeedSecond", this.links.getFirstFreqB().saveOptional(registries));
        freq.put("DirFirst", this.links.getSecondFreqA().saveOptional(registries));
        freq.put("DirSecond", this.links.getSecondFreqB().saveOptional(registries));
        tag.put("Frequency", freq);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.redstonePower = tag.getInt("RedstonePower");
        this.directionActive = tag.getBoolean("DirectionActive");
        if (!clientPacket) {
            this.reattachPending = tag.getBoolean("ReattachPending");
            TransmissionTrace.note(this, "read from tag");
        }

        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            this.links
                .setFirstFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("SpeedFirst")), ItemStack.parseOptional(registries, freq.getCompound("SpeedSecond"))
                );
            this.links
                .setSecondFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("DirFirst")), ItemStack.parseOptional(registries, freq.getCompound("DirSecond"))
                );
        }
    }
}
