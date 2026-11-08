package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.config.command.ConfigWalker;
import com.lycoris.loconautics.network.LoconauticsNetwork;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class AnalogControllerBlockEntity extends SmartBlockEntity implements MenuProvider, LinkHolder {
    private static final int RAISE_TICKS = 10;
    private static final int COUNTER_TICKS = 5;
    private static final int MAX_POWER = 15;
    private static final int HORN_HOLD_TICKS = 4;
    private static final int KEY_EXPIRE_TICKS = 30;
    public final LerpedFloat animSpeed = LerpedFloat.linear().startWithValue(0.0);
    public final LerpedFloat animSteer = LerpedFloat.linear().startWithValue(0.0);
    public final LerpedFloat animEquip = LerpedFloat.linear().startWithValue(0.0);
    @Nullable
    private UUID currentUser;
    private boolean locked;
    private boolean raisingSignal;
    private boolean loweringSignal;
    private int steerInput;
    private boolean spaceSignal;
    private int driveInput;
    private boolean lockSignal;
    private long sprintDownAt;
    private long lastKeyTick;
    private boolean sprintHeldForHorn;
    @Nullable
    private BlockPos horn;
    private double cruiseSpeed = Double.NaN;
    private int currentPower;
    private boolean scheduleControlled;
    private Integer scheduleTargetPower;
    private int scheduleRampCounter;
    private int maxPower = 15;
    private ItemStack frequencyFirst = ItemStack.EMPTY;
    private ItemStack frequencySecond = ItemStack.EMPTY;
    private ItemStack frequencyBackFirst = ItemStack.EMPTY;
    private ItemStack frequencyBackSecond = ItemStack.EMPTY;
    private ItemStack frequencyLeftFirst = ItemStack.EMPTY;
    private ItemStack frequencyLeftSecond = ItemStack.EMPTY;
    private ItemStack frequencyRightFirst = ItemStack.EMPTY;
    private ItemStack frequencyRightSecond = ItemStack.EMPTY;
    @Nullable
    private AnalogControllerBlockEntity.AnalogLinkEntry linkEntry;
    @Nullable
    private AnalogControllerBlockEntity.AnalogLinkEntry backLinkEntry;
    @Nullable
    private AnalogControllerBlockEntity.AnalogLinkEntry leftLinkEntry;
    @Nullable
    private AnalogControllerBlockEntity.AnalogLinkEntry rightLinkEntry;
    private int decayCounter = 10;
    private static final long TOGGLE_DEBOUNCE_TICKS = 3L;
    private long lastToggleTick = -3L;

    public AnalogControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            this.updateNetwork(this.level);
            this.updateBlockState(this.level, this.getBlockState());
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AnalogControllerBlockEntity be) {
        if (level.isClientSide) {
            be.clientTick();
        } else {
            be.serverTick(level, state);
        }
    }

    private void clientTick() {
        if (this.hasUser()) {
            this.animSpeed.chase(AnalogControllerClientHandler.speedBarRatio(this), 0.5, Chaser.EXP);
            this.animSteer.chase(this.steerInput, 0.2, Chaser.EXP);
            this.animEquip.chase(1.0, 0.2, Chaser.EXP);
        } else {
            this.animSpeed.chase(0.0, 0.0, Chaser.EXP);
            this.animSteer.chase(0.0, 0.0, Chaser.EXP);
            this.animEquip.chase(0.0, 0.2, Chaser.EXP);
        }

        this.animSpeed.tickChaser();
        this.animSteer.tickChaser();
        this.animEquip.tickChaser();
    }

    private static int leverTicks(int power, int delta) {
        return power != 0 && Integer.signum(power) != Integer.signum(delta) ? 5 : 10;
    }

    private void serverTick(Level level, BlockState state) {
        if (this.currentUser != null && level.getPlayerByUUID(this.currentUser) == null) {
            this.disconnectUser(level);
        }

        if (this.currentUser != null && this.anyKeyHeld() && level.getGameTime() - this.lastKeyTick > 30L) {
            this.expireHeldKeys(level);
        }

        int previousPower = this.currentPower;
        if (this.currentUser == null && !this.locked && !this.scheduleControlled && this.currentPower != 0) {
            if (--this.decayCounter <= 0) {
                this.decayCounter = this.getDecayTicks();
                this.currentPower = this.currentPower - Integer.signum(this.currentPower);
            }
        } else {
            this.decayCounter = this.getDecayTicks();
        }

        if (this.scheduleControlled && this.scheduleTargetPower != null && this.currentPower != this.scheduleTargetPower) {
            int interval = leverTicks(this.currentPower, Integer.signum(this.scheduleTargetPower - this.currentPower));
            if (--this.scheduleRampCounter <= 0) {
                this.scheduleRampCounter = interval;
                this.currentPower = this.currentPower + Integer.signum(this.scheduleTargetPower - this.currentPower);
            }
        }

        if (this.currentPower != previousPower) {
            this.updateNetwork(level);
            this.updateBlockState(level, state);
            this.setChanged();
            this.sendData();
        }
    }

    private int getDecayTicks() {
        return ConfigWalker.getOrDefault(LoconauticsConfig.ANALOG_DECAY_TICKS::get, 30, "analog_decay_ticks");
    }

    private int getNegativeCap() {
        return ConfigWalker.getOrDefault(LoconauticsConfig.ANALOG_NEGATIVE_CAP::get, 5, "analog_negative_cap");
    }

    public void toggleUser(Player player) {
        if (this.level != null) {
            long now = this.level.getGameTime();
            if (now - this.lastToggleTick >= 3L) {
                this.lastToggleTick = now;
                if (this.currentUser != null) {
                    if (this.currentUser.equals(player.getUUID())) {
                        this.disconnectUser(this.level);
                    }
                } else {
                    this.connectUser(player);
                }
            }
        }
    }

    public void resumeUser(ServerPlayer player) {
        this.connectUser(player);
        if (!this.locked) {
            this.applyDriveInput();
        }
    }

    public void releaseUser(UUID user) {
        if (this.level != null && user.equals(this.currentUser)) {
            this.disconnectUser(this.level);
        }
    }

    private boolean anyKeyHeld() {
        return this.raisingSignal || this.loweringSignal || this.steerInput != 0 || this.spaceSignal || this.lockSignal;
    }

    private void expireHeldKeys(Level level) {
        if (this.sprintHeldForHorn && this.horn != null && level.getPlayerByUUID(this.currentUser) instanceof ServerPlayer player) {
            TrainHorn.sound(player, this.horn, false);
        }

        this.raisingSignal = false;
        this.loweringSignal = false;
        this.spaceSignal = false;
        this.lockSignal = false;
        this.sprintHeldForHorn = false;
        this.horn = null;
        this.setSteerInput(0);
        this.updateDriveInput();
    }

    private void connectUser(Player player) {
        this.currentUser = player.getUUID();
        this.lastKeyTick = this.level.getGameTime();
        this.raisingSignal = false;
        this.loweringSignal = false;
        this.spaceSignal = false;
        this.driveInput = 0;
        this.updateBlockState(this.level, this.getBlockState());
        this.setChanged();
        this.sendData();
        if (!this.level.isClientSide && player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("block.loconautics.analog_controller.start"), true);
            LoconauticsNetwork.sendMount(sp, true, this.worldPosition);
        }
    }

    private void disconnectUser(Level level) {
        if (this.currentUser != null && level.getServer() != null) {
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(this.currentUser);
            if (sp != null) {
                sp.displayClientMessage(Component.translatable("block.loconautics.analog_controller.stop"), true);
                LoconauticsNetwork.sendMount(sp, false, BlockPos.ZERO);
            }
        }

        this.currentUser = null;
        this.raisingSignal = false;
        this.loweringSignal = false;
        this.spaceSignal = false;
        this.steerInput = 0;
        this.lockSignal = false;
        this.sprintHeldForHorn = false;
        this.horn = null;
        if (!this.locked) {
            this.driveInput = 0;
            if (!this.scheduleControlled) {
                this.currentPower = 0;
            }
        }

        this.updateNetwork(level);
        this.updateBlockState(level, this.getBlockState());
        this.setChanged();
        this.sendData();
    }

    public void onKeyEvent(UUID user, int keyIndex, boolean pressed) {
        if (user.equals(this.currentUser)) {
            if (pressed) {
                this.lastKeyTick = this.level.getGameTime();
            }

            switch (keyIndex) {
                case 0:
                    this.raisingSignal = pressed;
                    if (pressed) {
                        this.loweringSignal = false;
                    }

                    this.updateDriveInput();
                    break;
                case 1:
                    this.loweringSignal = pressed;
                    if (pressed) {
                        this.raisingSignal = false;
                    }

                    this.updateDriveInput();
                    break;
                case 2:
                    if (pressed) {
                        this.setSteerInput(-1);
                    } else if (this.steerInput == -1) {
                        this.setSteerInput(0);
                    }
                    break;
                case 3:
                    if (pressed) {
                        this.setSteerInput(1);
                    } else if (this.steerInput == 1) {
                        this.setSteerInput(0);
                    }
                    break;
                case 4:
                    this.spaceSignal = pressed;
                    break;
                case 5:
                    if (pressed) {
                        this.disconnectUser(this.level);
                    }
                    break;
                case 6:
                    this.onSprint(user, pressed);
                    this.lockSignal = pressed;
            }
        }
    }

    private void onSprint(UUID user, boolean pressed) {
        long now = this.level.getGameTime();
        if (pressed && !this.lockSignal) {
            this.sprintDownAt = now;
            this.sprintHeldForHorn = false;
        } else if (pressed && now - this.sprintDownAt >= 4L) {
            if (!this.sprintHeldForHorn && this.level instanceof ServerLevel serverLevel) {
                this.horn = TrainHorn.nearestWhistle(serverLevel, this.worldPosition);
            }

            this.sprintHeldForHorn = true;
            if (this.horn != null && this.level.getPlayerByUUID(user) instanceof ServerPlayer player) {
                TrainHorn.sound(player, this.horn, true);
            }
        } else if (!pressed && this.lockSignal) {
            if (this.sprintHeldForHorn) {
                if (this.horn != null && this.level.getPlayerByUUID(user) instanceof ServerPlayer player) {
                    TrainHorn.sound(player, this.horn, false);
                }

                this.horn = null;
                this.sprintHeldForHorn = false;
            } else {
                this.locked = !this.locked;
                this.cruiseSpeed = Double.NaN;
                this.updateDriveInput();
                this.setChanged();
                this.sendData();
            }
        }
    }

    private void updateDriveInput() {
        int held = this.heldInput();
        if (held != 0 || !this.locked) {
            this.driveInput = held;
        }

        this.applyDriveInput();
    }

    private void applyDriveInput() {
        if (!this.scheduleControlled) {
            int power = this.driveInput > 0 ? this.maxPower : (this.driveInput < 0 ? -Math.min(this.maxPower, this.getNegativeCap()) : 0);
            this.setCurrentPower(power);
        }
    }

    public int heldInput() {
        return this.raisingSignal ? 1 : (this.loweringSignal ? -1 : 0);
    }

    public double cruiseSpeed() {
        return this.cruiseSpeed;
    }

    public void setCruiseSpeed(double speed) {
        this.cruiseSpeed = speed;
    }

    public boolean isSpaceHeld() {
        return this.spaceSignal;
    }

    public int getSteerInput() {
        return this.steerInput;
    }

    private void setSteerInput(int value) {
        if (this.steerInput != value) {
            this.steerInput = value;
            if (this.level != null && !this.level.isClientSide) {
                this.updateNetwork(this.level);
            }

            this.setChanged();
            this.sendData();
        }
    }

    public boolean isForwardHeld() {
        return this.raisingSignal;
    }

    public boolean isReverseHeld() {
        return this.loweringSignal;
    }

    public void setScheduleTargetPower(int target) {
        this.scheduleControlled = true;
        this.scheduleTargetPower = Math.max(-15, Math.min(15, target));
    }

    public void setScheduleSteer(int dir) {
        this.scheduleControlled = true;
        this.setSteerInput(Integer.signum(dir));
    }

    public void setCurrentPower(int power) {
        int clamped = Math.max(-15, Math.min(15, power));
        if (this.currentPower != clamped) {
            this.currentPower = clamped;
            if (this.level != null && !this.level.isClientSide) {
                this.updateNetwork(this.level);
                this.updateBlockState(this.level, this.getBlockState());
            }

            this.setChanged();
            this.sendData();
        }
    }

    public void setLocked(boolean value) {
        if (this.locked != value) {
            this.locked = value;
            this.cruiseSpeed = Double.NaN;
            if (!this.locked) {
                this.driveInput = this.heldInput();
            }

            this.setChanged();
            this.sendData();
        }
    }

    public void clearScheduleControl() {
        if (this.scheduleControlled) {
            this.scheduleControlled = false;
            this.scheduleTargetPower = null;
            this.setSteerInput(0);
            this.applyDriveInput();
        }
    }

    public boolean isScheduleControlled() {
        return this.scheduleControlled;
    }

    private void updateNetwork(Level level) {
        if (level != null && !level.isClientSide) {
            Frequency fwdFirst = Frequency.of(this.frequencyFirst);
            Frequency fwdSecond = Frequency.of(this.frequencySecond);
            Frequency backFirst = Frequency.of(this.frequencyBackFirst);
            Frequency backSecond = Frequency.of(this.frequencyBackSecond);
            boolean hasFwdFreq = fwdFirst != Frequency.EMPTY || fwdSecond != Frequency.EMPTY;
            boolean hasBackFreq = backFirst != Frequency.EMPTY || backSecond != Frequency.EMPTY;
            Frequency leftFirst = Frequency.of(this.frequencyLeftFirst);
            Frequency leftSecond = Frequency.of(this.frequencyLeftSecond);
            Frequency rightFirst = Frequency.of(this.frequencyRightFirst);
            Frequency rightSecond = Frequency.of(this.frequencyRightSecond);
            boolean hasLeftFreq = leftFirst != Frequency.EMPTY || leftSecond != Frequency.EMPTY;
            boolean hasRightFreq = rightFirst != Frequency.EMPTY || rightSecond != Frequency.EMPTY;
            int fwdPower = this.currentPower != 0 ? Math.abs(this.currentPower) : 0;
            boolean goingBack = this.currentPower < 0;
            AnalogControllerBlockEntity.AnalogLinkEntry newLink = null;
            AnalogControllerBlockEntity.AnalogLinkEntry newBackLink = null;
            AnalogControllerBlockEntity.AnalogLinkEntry newLeftLink = null;
            AnalogControllerBlockEntity.AnalogLinkEntry newRightLink = null;
            if (hasFwdFreq && fwdPower > 0) {
                newLink = new AnalogControllerBlockEntity.AnalogLinkEntry(this, this.worldPosition, Couple.create(fwdFirst, fwdSecond), fwdPower);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newLink);
            }

            if (hasBackFreq && goingBack) {
                newBackLink = new AnalogControllerBlockEntity.AnalogLinkEntry(this, this.worldPosition, Couple.create(backFirst, backSecond), 15);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newBackLink);
            }

            if (hasLeftFreq && this.steerInput < 0) {
                newLeftLink = new AnalogControllerBlockEntity.AnalogLinkEntry(this, this.worldPosition, Couple.create(leftFirst, leftSecond), 15);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newLeftLink);
            }

            if (hasRightFreq && this.steerInput > 0) {
                newRightLink = new AnalogControllerBlockEntity.AnalogLinkEntry(this, this.worldPosition, Couple.create(rightFirst, rightSecond), 15);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newRightLink);
            }

            if (this.linkEntry != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this.linkEntry);
            }

            if (this.backLinkEntry != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this.backLinkEntry);
            }

            if (this.leftLinkEntry != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this.leftLinkEntry);
            }

            if (this.rightLinkEntry != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this.rightLinkEntry);
            }

            this.linkEntry = newLink;
            this.backLinkEntry = newBackLink;
            this.leftLinkEntry = newLeftLink;
            this.rightLinkEntry = newRightLink;
        }
    }

    private void updateBlockState(Level level, BlockState state) {
        boolean active = this.currentUser != null;
        boolean powered = this.currentPower != 0;
        int power = Math.abs(this.currentPower);
        BlockState newState = (BlockState)((BlockState)((BlockState)state.setValue(AnalogControllerBlock.POWER, power))
                .setValue(AnalogControllerBlock.ACTIVE, active))
            .setValue(AnalogControllerBlock.POWERED, powered);
        if (!newState.equals(state)) {
            level.setBlockAndUpdate(this.worldPosition, newState);
            level.updateNeighborsAt(this.worldPosition, newState.getBlock());
        }
    }

    public void onRemoved() {
        if (this.level != null) {
            if (this.currentUser != null && !this.level.isClientSide) {
                if (this.level.getPlayerByUUID(this.currentUser) instanceof ServerPlayer sp) {
                    LoconauticsNetwork.sendMount(sp, false, BlockPos.ZERO);
                }

                this.currentUser = null;
            }

            this.releaseLinks();
        }
    }

    @Override
    public void releaseLinks() {
        if (this.level != null) {
            if (this.linkEntry != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, this.linkEntry);
                this.linkEntry = null;
            }

            if (this.backLinkEntry != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, this.backLinkEntry);
                this.backLinkEntry = null;
            }

            if (this.leftLinkEntry != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, this.leftLinkEntry);
                this.leftLinkEntry = null;
            }

            if (this.rightLinkEntry != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, this.rightLinkEntry);
                this.rightLinkEntry = null;
            }
        }
    }

    public void invalidate() {
        this.releaseLinks();
        super.invalidate();
    }

    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.releaseLinks();
    }

    public void setFrequency(ItemStack first, ItemStack second) {
        this.frequencyFirst = first.copy();
        this.frequencySecond = second.copy();
        if (this.level != null && !this.level.isClientSide) {
            this.updateNetwork(this.level);
        }

        this.setChanged();
        this.sendData();
    }

    public void setBackwardFrequency(ItemStack first, ItemStack second) {
        this.frequencyBackFirst = first.copy();
        this.frequencyBackSecond = second.copy();
        if (this.level != null && !this.level.isClientSide) {
            this.updateNetwork(this.level);
        }

        this.setChanged();
        this.sendData();
    }

    public void setLeftFrequency(ItemStack first, ItemStack second) {
        this.frequencyLeftFirst = first.copy();
        this.frequencyLeftSecond = second.copy();
        if (this.level != null && !this.level.isClientSide) {
            this.updateNetwork(this.level);
        }

        this.setChanged();
        this.sendData();
    }

    public void setRightFrequency(ItemStack first, ItemStack second) {
        this.frequencyRightFirst = first.copy();
        this.frequencyRightSecond = second.copy();
        if (this.level != null && !this.level.isClientSide) {
            this.updateNetwork(this.level);
        }

        this.setChanged();
        this.sendData();
    }

    public ItemStack getFrequencyFirst() {
        return this.frequencyFirst;
    }

    public ItemStack getFrequencySecond() {
        return this.frequencySecond;
    }

    public ItemStack getFrequencyBackFirst() {
        return this.frequencyBackFirst;
    }

    public ItemStack getFrequencyBackSecond() {
        return this.frequencyBackSecond;
    }

    public ItemStack getFrequencyLeftFirst() {
        return this.frequencyLeftFirst;
    }

    public ItemStack getFrequencyLeftSecond() {
        return this.frequencyLeftSecond;
    }

    public ItemStack getFrequencyRightFirst() {
        return this.frequencyRightFirst;
    }

    public ItemStack getFrequencyRightSecond() {
        return this.frequencyRightSecond;
    }

    public int getCurrentPower() {
        return this.currentPower;
    }

    public boolean isLocked() {
        return this.locked;
    }

    public boolean hasUser() {
        return this.currentUser != null;
    }

    public int getMaxPower() {
        return this.maxPower;
    }

    @Nullable
    public UUID getCurrentUser() {
        return this.currentUser;
    }

    public void onScroll(UUID user, int delta) {
        if (user.equals(this.currentUser)) {
            this.maxPower = Math.max(0, Math.min(15, this.maxPower + delta));
            if (this.driveInput != 0) {
                this.applyDriveInput();
            }

            this.setChanged();
            this.sendData();
        }
    }

    public Component getDisplayName() {
        return Component.translatable("block.loconautics.analog_controller");
    }

    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AnalogControllerMenu((MenuType<?>)LoconauticsRegistries.ANALOG_CONTROLLER_MENU.get(), id, inventory, this);
    }

    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.translatable("gui.loconautics.bind_frequency.speed"));
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.translatable("gui.loconautics.bind_frequency.direction"));
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.translatable("gui.loconautics.bind_frequency.left"));
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.translatable("gui.loconautics.bind_frequency.right"));
        super.sendToMenu(buffer);
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Power", this.currentPower);
        tag.putBoolean("Locked", this.locked);
        tag.putInt("MaxPower", this.maxPower);
        if (this.currentUser != null) {
            tag.putUUID("User", this.currentUser);
        }

        if (clientPacket) {
            tag.putBoolean("HasUser", this.currentUser != null);
            tag.putInt("Steer", this.steerInput);
        }

        CompoundTag freq = new CompoundTag();
        freq.put("First", this.frequencyFirst.saveOptional(registries));
        freq.put("Second", this.frequencySecond.saveOptional(registries));
        freq.put("BackFirst", this.frequencyBackFirst.saveOptional(registries));
        freq.put("BackSecond", this.frequencyBackSecond.saveOptional(registries));
        freq.put("LeftFirst", this.frequencyLeftFirst.saveOptional(registries));
        freq.put("LeftSecond", this.frequencyLeftSecond.saveOptional(registries));
        freq.put("RightFirst", this.frequencyRightFirst.saveOptional(registries));
        freq.put("RightSecond", this.frequencyRightSecond.saveOptional(registries));
        tag.put("Frequency", freq);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.currentPower = tag.getInt("Power");
        this.locked = tag.getBoolean("Locked");
        this.maxPower = tag.contains("MaxPower") ? tag.getInt("MaxPower") : 15;
        if (clientPacket && tag.contains("HasUser")) {
            this.currentUser = tag.getBoolean("HasUser") ? UUID.randomUUID() : null;
            this.steerInput = tag.getInt("Steer");
        } else {
            this.currentUser = null;
        }

        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            this.frequencyFirst = ItemStack.parseOptional(registries, freq.getCompound("First"));
            this.frequencySecond = ItemStack.parseOptional(registries, freq.getCompound("Second"));
            this.frequencyBackFirst = ItemStack.parseOptional(registries, freq.getCompound("BackFirst"));
            this.frequencyBackSecond = ItemStack.parseOptional(registries, freq.getCompound("BackSecond"));
            this.frequencyLeftFirst = ItemStack.parseOptional(registries, freq.getCompound("LeftFirst"));
            this.frequencyLeftSecond = ItemStack.parseOptional(registries, freq.getCompound("LeftSecond"));
            this.frequencyRightFirst = ItemStack.parseOptional(registries, freq.getCompound("RightFirst"));
            this.frequencyRightSecond = ItemStack.parseOptional(registries, freq.getCompound("RightSecond"));
        }
    }

    public static class AnalogLinkEntry implements IRedstoneLinkable {
        private final AnalogControllerBlockEntity owner;
        private final BlockPos pos;
        private final Couple<Frequency> key;
        private final int power;

        public AnalogLinkEntry(AnalogControllerBlockEntity owner, BlockPos pos, Couple<Frequency> key, int power) {
            this.owner = owner;
            this.pos = pos;
            this.key = key;
            this.power = power;
        }

        public int getTransmittedStrength() {
            return this.power;
        }

        public void setReceivedStrength(int strength) {
        }

        public boolean isListening() {
            return false;
        }

        public boolean isAlive() {
            return !this.owner.isRemoved() && this.owner.getLevel() != null && this.owner.getLevel().getBlockEntity(this.pos) == this.owner;
        }

        public Couple<Frequency> getNetworkKey() {
            return this.key;
        }

        public BlockPos getLocation() {
            return this.pos;
        }
    }
}
