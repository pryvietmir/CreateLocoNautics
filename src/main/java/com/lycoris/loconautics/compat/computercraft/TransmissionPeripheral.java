package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public class TransmissionPeripheral implements IPeripheral {
    private static final Map<TransmissionBlockEntity, TransmissionPeripheral> CACHE = new WeakHashMap<>();
    private final TransmissionBlockEntity transmission;

    private TransmissionPeripheral(TransmissionBlockEntity transmission) {
        this.transmission = transmission;
    }

    public static TransmissionPeripheral of(TransmissionBlockEntity transmission) {
        return CACHE.computeIfAbsent(transmission, TransmissionPeripheral::new);
    }

    public String getType() {
        return "loconautics:transmission";
    }

    public boolean equals(IPeripheral other) {
        return other instanceof TransmissionPeripheral p && p.transmission == this.transmission;
    }

    public void attach(IComputerAccess computer) {
    }

    public void detach(IComputerAccess computer) {
    }

    @LuaFunction(mainThread = true)
    public final int getPower() {
        return this.transmission.getPower();
    }

    @LuaFunction(mainThread = true)
    public final double getTargetRpm() {
        return this.transmission.getTargetRpm();
    }

    @LuaFunction(mainThread = true)
    public final double getInputRpm() {
        return this.transmission.getSpeed();
    }

    @LuaFunction(mainThread = true)
    public final double getOutputRpm() {
        if (!this.transmission.hasSource()) {
            return 0.0;
        }

        Direction output = this.transmission.getSourceFacing().getOpposite();
        return this.transmission.getSpeed() * this.transmission.getRotationSpeedModifier(output);
    }

    @LuaFunction(mainThread = true)
    public final boolean isReversed() {
        return this.transmission.isReversed();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, String> getSpeedFrequency() {
        return frequency(this.transmission.getSpeedFreqFirst(), this.transmission.getSpeedFreqSecond());
    }

    @LuaFunction(mainThread = true)
    public final Map<String, String> getDirectionFrequency() {
        return frequency(this.transmission.getDirFreqFirst(), this.transmission.getDirFreqSecond());
    }

    @LuaFunction(mainThread = true)
    public final boolean isComputerControlled() {
        return this.transmission.isComputerDriven();
    }

    @LuaFunction(mainThread = true)
    public final boolean isConnected() throws LuaException {
        return this.connection().ok();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getConnection() throws LuaException {
        return CcDrivetrain.report(this.connection());
    }

    private LocomotiveHardware.Drivetrain connection() throws LuaException {
        return CcDrivetrain.check(this.transmission.getLevel(), this.transmission.getBlockPos());
    }

    private static Map<String, String> frequency(ItemStack first, ItemStack second) {
        return Map.of("first", idOf(first), "second", idOf(second));
    }

    private static String idOf(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
