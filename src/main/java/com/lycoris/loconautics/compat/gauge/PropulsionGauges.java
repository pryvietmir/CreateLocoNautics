package com.lycoris.loconautics.compat.gauge;

import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.propulsionteam.propulsionsimulated.PropulsionConfig;
import dev.propulsionteam.propulsionsimulated.content.heat.burners.AbstractBurnerBlock;
import dev.propulsionteam.propulsionsimulated.content.heat.burners.liquid.LiquidBurnerBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.ion_thruster.IonThrusterBlockEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class PropulsionGauges {
    private PropulsionGauges() {
    }

    public static List<Class<? extends BlockEntity>> thrusterSources() {
        return List.of(AbstractThrusterBlockEntity.class);
    }

    public static List<Class<? extends BlockEntity>> burnerSources() {
        return List.of(LiquidBurnerBlockEntity.class);
    }

    @Nullable
    public static String thrusterFuel(BlockEntity be) {
        return be instanceof AbstractThrusterBlockEntity thruster && thruster.getFuelCapacityMb() > 0
            ? String.format(Locale.US, "%,d / %,d mB", thruster.getFuelAmountMb(), thruster.getFuelCapacityMb())
            : null;
    }

    @Nullable
    public static Float thrusterFuelFraction(BlockEntity be) {
        return be instanceof AbstractThrusterBlockEntity thruster ? GaugeMetric.over(thruster.getFuelAmountMb(), thruster.getFuelCapacityMb()) : null;
    }

    @Nullable
    public static String thrusterThrottle(BlockEntity be) {
        return be instanceof AbstractThrusterBlockEntity thruster ? GaugeMetric.pct(thruster.getPower()) : null;
    }

    @Nullable
    public static Float thrusterPower(BlockEntity be) {
        return be instanceof AbstractThrusterBlockEntity thruster ? thruster.getPower() : null;
    }

    @Nullable
    public static String thrusterThrust(BlockEntity be) {
        return be instanceof AbstractThrusterBlockEntity thruster
            ? String.format(Locale.US, "%,.2f pN", thruster.getCurrentThrust() / PropulsionConfig.getThrustUnitsPerKnOrDefault())
            : null;
    }

    @Nullable
    public static String ionEnergy(BlockEntity be) {
        return be instanceof IonThrusterBlockEntity ion ? String.format(Locale.US, "%,d / %,d FE", ion.getEnergyStoredFe(), ion.getEnergyCapacity()) : null;
    }

    @Nullable
    public static Float ionEnergyFraction(BlockEntity be) {
        return be instanceof IonThrusterBlockEntity ion ? GaugeMetric.over(ion.getEnergyStoredFe(), ion.getEnergyCapacity()) : null;
    }

    @Nullable
    private static IFluidHandler burnerTank(BlockEntity be) {
        return be instanceof LiquidBurnerBlockEntity burner ? burner.getPrimaryFluidHandler() : null;
    }

    @Nullable
    public static String burnerFuel(BlockEntity be) {
        IFluidHandler tank = burnerTank(be);
        return tank == null ? null : String.format(Locale.US, "%,d / %,d mB", tank.getFluidInTank(0).getAmount(), tank.getTankCapacity(0));
    }

    @Nullable
    public static Float burnerFuelFraction(BlockEntity be) {
        IFluidHandler tank = burnerTank(be);
        return tank == null ? null : GaugeMetric.over(tank.getFluidInTank(0).getAmount(), tank.getTankCapacity(0));
    }

    @Nullable
    public static String burnerTime(BlockEntity be) {
        return be instanceof LiquidBurnerBlockEntity burner ? FireboxBlockEntity.formatTime(burner.getBurnTime() / 20) : null;
    }

    @Nullable
    public static Float burnerTimeFraction(BlockEntity be) {
        return be instanceof LiquidBurnerBlockEntity burner ? GaugeMetric.over(burner.getBurnTime(), 1200.0F) : null;
    }

    @Nullable
    public static String burnerHeat(BlockEntity be) {
        if (be instanceof LiquidBurnerBlockEntity burner) {
            String name = ((HeatLevel)burner.getBlockState().getValue(AbstractBurnerBlock.HEAT)).name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        } else {
            return null;
        }
    }

    @Nullable
    public static Float burnerHeatFraction(BlockEntity be) {
        if (be instanceof LiquidBurnerBlockEntity burner) {
            HeatLevel level = (HeatLevel)burner.getBlockState().getValue(AbstractBurnerBlock.HEAT);
            return (float)level.ordinal() / (HeatLevel.values().length - 1);
        } else {
            return null;
        }
    }
}
