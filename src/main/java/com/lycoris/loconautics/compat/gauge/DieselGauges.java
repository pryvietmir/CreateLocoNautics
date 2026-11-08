package com.lycoris.loconautics.compat.gauge;

import com.jesz.createdieselgenerators.content.diesel_engine.IEngine;
import com.jesz.createdieselgenerators.content.diesel_engine.huge.HugeDieselEngineBlockEntity;
import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity;
import com.jesz.createdieselgenerators.content.diesel_engine.normal.DieselEngineBlockEntity;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public final class DieselGauges {
    private DieselGauges() {
    }

    public static List<Class<? extends BlockEntity>> sources() {
        return List.of(DieselEngineBlockEntity.class, ModularDieselEngineBlockEntity.class, HugeDieselEngineBlockEntity.class);
    }

    @Nullable
    private static FluidTank tank(BlockEntity be) {
        return be instanceof IEngine engine ? engine.getTank() : null;
    }

    @Nullable
    public static String fuel(BlockEntity be) {
        FluidTank tank = tank(be);
        return tank == null ? null : String.format(Locale.US, "%,d / %,d mB", tank.getFluidAmount(), tank.getCapacity());
    }

    @Nullable
    public static Float fuelFraction(BlockEntity be) {
        FluidTank tank = tank(be);
        return tank == null ? null : GaugeMetric.over(tank.getFluidAmount(), tank.getCapacity());
    }

    @Nullable
    public static String fuelFill(BlockEntity be) {
        Float fraction = fuelFraction(be);
        return fraction == null ? null : GaugeMetric.pct(fraction);
    }

    private static float burnPerTick(IEngine engine) {
        return engine.getCachedBurnRate() * engine.getFuelThrottle();
    }

    @Nullable
    public static String burnsFor(BlockEntity be) {
        FluidTank tank = tank(be);
        if (tank != null && be instanceof IEngine engine) {
            float perTick = burnPerTick(engine);
            return perTick <= 0.0F
                ? Component.translatable("loconautics.gui.goggles.gauge_diesel_idle").getString()
                : FireboxBlockEntity.formatTime((int)(tank.getFluidAmount() / perTick / 20.0F));
        } else {
            return null;
        }
    }

    @Nullable
    public static Float burnsForFraction(BlockEntity be) {
        FluidTank tank = tank(be);
        if (tank != null && be instanceof IEngine engine) {
            float perTick = burnPerTick(engine);
            return perTick <= 0.0F ? 0.0F : GaugeMetric.over(tank.getFluidAmount() / perTick / 20.0F, 600.0F);
        } else {
            return null;
        }
    }

    @Nullable
    private static Float speed(BlockEntity be) {
        if (be instanceof DieselEngineBlockEntity engine) {
            return Math.abs(engine.getGeneratedSpeed());
        } else {
            return be instanceof ModularDieselEngineBlockEntity engine ? Math.abs(engine.getGeneratedSpeed()) : null;
        }
    }

    @Nullable
    public static String rpm(BlockEntity be) {
        Float speed = speed(be);
        return speed == null ? null : String.format(Locale.US, "%,.0f RPM", speed);
    }

    @Nullable
    public static Float rpmFraction(BlockEntity be) {
        Float speed = speed(be);
        return speed == null ? null : GaugeMetric.over(speed, 256.0F);
    }

    @Nullable
    public static String throttle(BlockEntity be) {
        return be instanceof IEngine engine ? GaugeMetric.pct(engine.getThrottle()) : null;
    }

    @Nullable
    public static Float throttleFraction(BlockEntity be) {
        return be instanceof IEngine engine ? engine.getThrottle() : null;
    }
}
