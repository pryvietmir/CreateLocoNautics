package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class UnknownInstruction extends ScheduleInstruction {
    private final ResourceLocation id;

    public UnknownInstruction(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public boolean supportsConditions() {
        return true;
    }

    @Nullable
    @Override
    public SchedulePath start(SableScheduleRuntime runtime, Level level) {
        return this.finishInstant(runtime);
    }
}
