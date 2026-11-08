package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ManeuverInstruction extends ScheduleInstruction {
    public ManeuverInstruction() {
        this.data.putInt("Value", 10);
    }

    @Override
    public ResourceLocation getId() {
        return LoconauticsConstants.id("maneuver");
    }

    public int percent() {
        int value = this.intData("Value");
        return value <= 0 ? 10 : Math.min(value, 75);
    }

    @Override
    public boolean supportsConditions() {
        return false;
    }

    @Nullable
    @Override
    public SchedulePath start(SableScheduleRuntime runtime, Level level) {
        runtime.startManeuver(runtime.former(), this.percent());
        return this.finishInstant(runtime);
    }
}
