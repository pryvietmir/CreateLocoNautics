package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstructionType;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class TurntableMotion {
    private static final int HOME_DELAY_TICKS = 40;
    private static final double DEGREE_EPSILON = 1.0E-4;
    private static final int ALIGN_SETTLE_TICKS = 5;
    private double lastTargetAngleDegrees;
    private double targetAngleDegrees;
    private List<TurntableInstruction> instructions = TurntableInstruction.defaultList();
    private TurntableTriggerMode trigger = TurntableTriggerMode.PULSE;
    private int currentInstruction = -1;
    private double remainingDegrees;
    private int delayTimer;
    private boolean poweredPreviously;
    private int directionSign = 1;
    private int homeDelay;
    @Nullable
    private TrackNodeLocation alignMovingHead;
    @Nullable
    private TrackNodeLocation alignTarget;
    private int alignedTicks;
    private double manualRemainingDegrees;
    private int manualSign = 1;

    public void beginTick() {
        this.lastTargetAngleDegrees = this.targetAngleDegrees;
    }

    public void step(TurntableMotion.Host host, boolean scheduleDriven) {
        if (scheduleDriven) {
            this.advanceAlignment(host);
        } else if (this.manualRemainingDegrees > 1.0E-4) {
            this.advanceManualTurn(host);
        } else {
            this.runSequence(host);
        }
    }

    public void copyFrom(TurntableMotion other) {
        this.lastTargetAngleDegrees = other.lastTargetAngleDegrees;
        this.targetAngleDegrees = other.targetAngleDegrees;
        this.instructions = TurntableInstruction.sanitize(other.instructions);
        this.trigger = other.trigger;
        this.currentInstruction = other.currentInstruction;
        this.remainingDegrees = other.remainingDegrees;
        this.delayTimer = other.delayTimer;
        this.poweredPreviously = other.poweredPreviously;
        this.directionSign = other.directionSign;
        this.homeDelay = other.homeDelay;
        this.alignMovingHead = other.alignMovingHead;
        this.alignTarget = other.alignTarget;
        this.alignedTicks = other.alignedTicks;
        this.manualRemainingDegrees = other.manualRemainingDegrees;
        this.manualSign = other.manualSign;
    }

    public void reset() {
        this.targetAngleDegrees = 0.0;
        this.lastTargetAngleDegrees = 0.0;
        this.currentInstruction = -1;
        this.manualRemainingDegrees = 0.0;
    }

    public double targetAngleDegrees() {
        return this.targetAngleDegrees;
    }

    public double lastTargetAngleDegrees() {
        return this.lastTargetAngleDegrees;
    }

    public List<TurntableInstruction> instructions() {
        return this.instructions;
    }

    public TurntableTriggerMode trigger() {
        return this.trigger;
    }

    public int currentInstruction() {
        return this.currentInstruction;
    }

    public double manualRemainingDegrees() {
        return this.manualRemainingDegrees;
    }

    public boolean scheduleDriven() {
        return TurntableInstruction.isScheduleDriven(this.instructions);
    }

    public boolean isTurning() {
        return this.manualRemainingDegrees > 1.0E-4 || this.currentInstruction >= 0;
    }

    public boolean settled() {
        return this.alignTarget == null && this.targetAngleDegrees == this.lastTargetAngleDegrees;
    }

    public boolean busy(@Nullable UUID plate) {
        return this.isTurning()
            || this.alignTarget != null
            || !this.settled()
            || Math.abs(Mth.wrapDegrees(this.targetAngleDegrees)) > 0.75
            || TurntableRegistry.trainOnPlate(plate)
            || TurntableRegistry.occupied(plate);
    }

    public void alignTo(TrackNodeLocation movingHead, TrackNodeLocation target) {
        this.alignMovingHead = movingHead;
        this.alignTarget = target;
        this.alignedTicks = 0;
    }

    public void clearAlignment() {
        this.alignMovingHead = null;
        this.alignTarget = null;
        this.alignedTicks = 0;
        this.homeDelay = 40;
    }

    @Nullable
    public TrackNodeLocation alignTarget() {
        return this.alignTarget;
    }

    public boolean aligned() {
        return this.alignTarget != null && this.alignMovingHead != null && this.alignedTicks >= 5;
    }

    public void turnBy(double degrees) {
        this.manualSign = degrees < 0.0 ? -1 : 1;
        this.manualRemainingDegrees = Math.abs(degrees);
        this.currentInstruction = -1;
        this.remainingDegrees = 0.0;
        this.delayTimer = 0;
    }

    public void turnTo(double absoluteDegrees) {
        this.turnBy(Mth.wrapDegrees(absoluteDegrees - this.targetAngleDegrees));
    }

    public void stopTurning(TurntableMotion.Host host) {
        this.manualRemainingDegrees = 0.0;
        this.currentInstruction = -1;
        this.remainingDegrees = 0.0;
        this.delayTimer = 0;
        host.motionChanged();
    }

    public void runInstruction(TurntableMotion.Host host, int index) {
        this.manualRemainingDegrees = 0.0;
        this.run(host, index);
    }

    public void configure(TurntableMotion.Host host, List<TurntableInstruction> newInstructions, TurntableTriggerMode newTrigger) {
        this.instructions = TurntableInstruction.sanitize(newInstructions);
        this.trigger = newTrigger;
        this.currentInstruction = -1;
        this.remainingDegrees = 0.0;
        this.delayTimer = 0;
        this.manualRemainingDegrees = 0.0;
        this.clearAlignment();
        host.motionChanged();
    }

    public void redstoneEdge(TurntableMotion.Host host, boolean isPowered, boolean scheduleDriven) {
        if (scheduleDriven) {
            this.poweredPreviously = isPowered;
        } else {
            if (!this.poweredPreviously && isPowered) {
                if (this.currentInstruction < 0) {
                    if (this.trigger == TurntableTriggerMode.ALTERNATE) {
                        this.directionSign = -this.directionSign;
                    }

                    this.run(host, 0);
                } else if (this.instructions.get(this.currentInstruction).type == TurntableInstructionType.AWAIT) {
                    this.run(host, this.currentInstruction + 1);
                }
            }

            this.poweredPreviously = isPowered;
        }
    }

    private void advanceAlignment(TurntableMotion.Host host) {
        UUID plate = host.plateSubLevelId();
        if (this.alignTarget != null && !TurntableRegistry.reserved(plate)) {
            this.clearAlignment();
        }

        if (this.alignTarget != null && this.alignMovingHead != null) {
            double error = TurntableRegistry.alignmentError(host, this.alignMovingHead, this.alignTarget);
            this.alignedTicks = Math.abs(error) <= 0.75 ? this.alignedTicks + 1 : 0;
            this.stepBy(host, error);
        } else {
            this.returnHome(host);
        }
    }

    private void returnHome(TurntableMotion.Host host) {
        if (this.homeDelay > 0) {
            this.homeDelay--;
        } else {
            UUID plate = host.plateSubLevelId();
            if (!TurntableRegistry.occupied(plate) && !TurntableRegistry.reserved(plate)) {
                this.stepBy(host, -Mth.wrapDegrees(this.targetAngleDegrees));
            }
        }
    }

    private void stepBy(TurntableMotion.Host host, double error) {
        if (!(Math.abs(error) <= 0.75)) {
            double perTick = Math.abs(host.angularPerTick());
            if (perTick != 0.0) {
                this.targetAngleDegrees = (this.targetAngleDegrees + Math.min(perTick, Math.abs(error)) * Math.signum(error)) % 360.0;
                TurntableRegistry.unweldPlateSeams(host.plateSubLevelId());
                host.bodiesMoved();
            }
        }
    }

    private void advanceManualTurn(TurntableMotion.Host host) {
        double perTick = Math.abs(host.angularPerTick());
        if (perTick != 0.0) {
            int overrideSign = host.speedOverride() < 0.0F ? -1 : 1;
            double step = Math.min(perTick, this.manualRemainingDegrees) * this.manualSign * overrideSign;
            this.targetAngleDegrees = (this.targetAngleDegrees + step) % 360.0;
            this.manualRemainingDegrees = this.manualRemainingDegrees - Math.abs(step);
            TurntableRegistry.unweldPlateSeams(host.plateSubLevelId());
            host.bodiesMoved();
            if (this.manualRemainingDegrees <= 1.0E-4) {
                this.manualRemainingDegrees = 0.0;
                host.motionChanged();
            }
        }
    }

    private void runSequence(TurntableMotion.Host host) {
        if (this.currentInstruction >= 0 && this.currentInstruction < this.instructions.size()) {
            TurntableInstruction instruction = this.instructions.get(this.currentInstruction);
            switch (instruction.type) {
                case TURN_ANGLE:
                    this.advanceTurn(host, instruction);
                    break;
                case DELAY:
                    if (--this.delayTimer <= 0) {
                        this.run(host, this.currentInstruction + 1);
                    }
                case AWAIT:
                default:
                    break;
                case END:
                    this.run(host, -1);
            }
        }
    }

    private void advanceTurn(TurntableMotion.Host host, TurntableInstruction instruction) {
        float perTick = host.angularPerTick() * instruction.signedSpeedModifier() * this.directionSign;
        if (host.facingNegative()) {
            perTick = -perTick;
        }

        if (perTick != 0.0F) {
            double step = Math.min(Math.abs(perTick), this.remainingDegrees) * Math.signum(perTick);
            this.targetAngleDegrees = (this.targetAngleDegrees + step) % 360.0;
            this.remainingDegrees = this.remainingDegrees - Math.abs(step);
            host.bodiesMoved();
            if (this.remainingDegrees <= 1.0E-4) {
                this.run(host, this.currentInstruction + 1);
            }
        }
    }

    private void run(TurntableMotion.Host host, int index) {
        if (index >= 0 && index < this.instructions.size() && this.instructions.get(index).type != TurntableInstructionType.END) {
            TurntableInstruction instruction = this.instructions.get(index);
            this.currentInstruction = index;
            this.remainingDegrees = instruction.type == TurntableInstructionType.TURN_ANGLE ? instruction.value : 0.0;
            this.delayTimer = instruction.type == TurntableInstructionType.DELAY ? instruction.value : 0;
            host.motionChanged();
        } else {
            this.currentInstruction = -1;
            this.remainingDegrees = 0.0;
            this.delayTimer = 0;
            if (this.trigger == TurntableTriggerMode.HOLD
                && host.neighborSignal()
                && !this.instructions.isEmpty()
                && this.instructions.get(0).type != TurntableInstructionType.END) {
                this.run(host, 0);
            } else {
                host.motionChanged();
            }
        }
    }

    public void write(CompoundTag compound) {
        compound.putDouble("TargetAngle", this.targetAngleDegrees);
        compound.put("Instructions", TurntableInstruction.writeList(this.instructions));
        compound.putString("Trigger", this.trigger.name());
        compound.putInt("InstructionIndex", this.currentInstruction);
        compound.putDouble("RemainingDegrees", this.remainingDegrees);
        compound.putInt("DelayTimer", this.delayTimer);
        compound.putInt("DirectionSign", this.directionSign);
        compound.putBoolean("PoweredPreviously", this.poweredPreviously);
        compound.putDouble("ManualRemaining", this.manualRemainingDegrees);
        compound.putInt("ManualSign", this.manualSign);
    }

    public void read(CompoundTag compound) {
        this.targetAngleDegrees = compound.getDouble("TargetAngle");
        this.lastTargetAngleDegrees = this.targetAngleDegrees;
        if (compound.contains("Instructions")) {
            this.instructions = TurntableInstruction.readList(compound.getList("Instructions", 10));
        }

        this.trigger = triggerOrDefault(compound.getString("Trigger"));
        this.currentInstruction = compound.contains("InstructionIndex") ? compound.getInt("InstructionIndex") : -1;
        this.remainingDegrees = compound.getDouble("RemainingDegrees");
        this.delayTimer = compound.getInt("DelayTimer");
        this.directionSign = compound.contains("DirectionSign") ? compound.getInt("DirectionSign") : 1;
        this.poweredPreviously = compound.getBoolean("PoweredPreviously");
        this.manualRemainingDegrees = compound.getDouble("ManualRemaining");
        this.manualSign = compound.contains("ManualSign") ? compound.getInt("ManualSign") : 1;
    }

    private static TurntableTriggerMode triggerOrDefault(String name) {
        for (TurntableTriggerMode mode : TurntableTriggerMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }

        return TurntableTriggerMode.PULSE;
    }

    public interface Host extends Turntable {
        float angularPerTick();

        boolean facingNegative();

        float speedOverride();

        boolean neighborSignal();

        void motionChanged();

        void bodiesMoved();
    }
}
