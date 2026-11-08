package com.lycoris.loconautics.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import java.util.Map;

public class CarriageHandle {
    private final LoconauticsTrainPeripheral train;
    private final int index;

    CarriageHandle(LoconauticsTrainPeripheral train, int index) {
        this.train = train;
        this.index = index;
    }

    @LuaFunction
    public final int getIndex() {
        return this.index;
    }

    @LuaFunction(mainThread = true)
    public final double getLength() throws LuaException {
        return this.train.getCarriageLength(this.index);
    }

    @LuaFunction(mainThread = true)
    public final double getWidth() throws LuaException {
        return this.train.getCarriageWidth(this.index);
    }

    @LuaFunction(mainThread = true)
    public final double getHeight() throws LuaException {
        return this.train.getCarriageHeight(this.index);
    }

    @LuaFunction(mainThread = true)
    public final int getBlockCount() throws LuaException {
        return this.train.getCarriageBlockCount(this.index);
    }

    @LuaFunction(mainThread = true)
    public final int getItemCount(IArguments args) throws LuaException {
        return this.train.carriageItemCount(this.index, (String)args.optString(0).orElse(null));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Integer> getItems() throws LuaException {
        return this.train.getCarriageItems(this.index);
    }

    @LuaFunction(mainThread = true)
    public final int getItemCapacity() throws LuaException {
        return this.train.getCarriageItemCapacity(this.index);
    }

    @LuaFunction(mainThread = true)
    public final int getFluidAmount(IArguments args) throws LuaException {
        return this.train.carriageFluidAmount(this.index, (String)args.optString(0).orElse(null));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Map<String, Integer>> getFluids() throws LuaException {
        return this.train.getCarriageFluids(this.index);
    }

    @LuaFunction(mainThread = true)
    public final int getFluidCapacity() throws LuaException {
        return this.train.getCarriageFluidCapacity(this.index);
    }

    @LuaFunction(mainThread = true)
    public final int getPassengerCount() throws LuaException {
        return this.train.getCarriagePassengerCount(this.index);
    }

    @LuaFunction(mainThread = true)
    public final boolean hasPassengers() throws LuaException {
        return this.train.getCarriageHasPassengers(this.index);
    }
}
