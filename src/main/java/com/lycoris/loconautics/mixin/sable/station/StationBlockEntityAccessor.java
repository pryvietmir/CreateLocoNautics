package com.lycoris.loconautics.mixin.sable.station;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = StationBlockEntity.class, remap = false)
public interface StationBlockEntityAccessor {
    @Accessor("bogeyCount")
    int loconautics$getBogeyCount();

    @Invoker("exception")
    void loconautics$exception(AssemblyException var1, int var2);

    @Accessor("trainPresent")
    void loconautics$setTrainPresent(boolean var1);

    @Accessor("trainCanDisassemble")
    void loconautics$setTrainCanDisassemble(boolean var1);
}
