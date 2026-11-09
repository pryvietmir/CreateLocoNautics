package com.lycoris.loconautics.mixin.create.schedule;

import com.lycoris.loconautics.content.trainmail.CreateTrainMail;
import com.lycoris.loconautics.content.trainmail.PostalState;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScheduleRuntime.class)
public abstract class ScheduleRuntimePostalMixin implements PostalState {
    @Shadow
    public State state;
    @Unique
    private boolean loconautics$tripped;
    @Unique
    private boolean loconautics$repeat;
    @Unique
    private boolean loconautics$deposit;
    @Unique
    private final Set<UUID> loconautics$warned = new HashSet<>();

    @Shadow
    public abstract void startCooldown();

    @Override
    public boolean loconautics$tripped() {
        return this.loconautics$tripped;
    }

    @Override
    public void loconautics$setTripped(boolean tripped) {
        this.loconautics$tripped = tripped;
        if (!tripped) {
            this.loconautics$warned.clear();
        }
    }

    @Override
    public void loconautics$setRepeat(boolean repeat) {
        this.loconautics$repeat = repeat;
    }

    @Override
    public boolean loconautics$repeat() {
        return this.loconautics$repeat;
    }

    @Override
    public boolean loconautics$deposit() {
        return this.loconautics$deposit;
    }

    @Override
    public void loconautics$setDeposit(boolean deposit) {
        this.loconautics$deposit = deposit;
    }

    @Override
    public Set<UUID> loconautics$warned() {
        return this.loconautics$warned;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void loconautics$postalRecheck(Level level, CallbackInfo ci) {
        CreateTrainMail.recheck((ScheduleRuntime)(Object)this, level);
    }

    @Inject(method = "destinationReached", at = @At("RETURN"))
    private void loconautics$postalArrival(CallbackInfo ci) {
        if (this.state == State.POST_TRANSIT) {
            if (this.loconautics$repeat) {
                this.loconautics$repeat = false;
                this.state = State.PRE_TRANSIT;
                this.startCooldown();
            } else {
                this.loconautics$setTripped(false);
            }
        }
    }
}
