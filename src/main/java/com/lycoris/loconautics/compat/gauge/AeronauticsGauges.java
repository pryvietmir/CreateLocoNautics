package com.lycoris.loconautics.compat.gauge;

import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity;
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class AeronauticsGauges {
    private AeronauticsGauges() {
    }

    public static List<Class<? extends BlockEntity>> bearingSources() {
        return List.of(PropellerBearingBlockEntity.class);
    }

    public static List<Class<? extends BlockEntity>> hotAirSources() {
        return List.of(HotAirBurnerBlockEntity.class);
    }

    public static List<Class<? extends BlockEntity>> steamSources() {
        return List.of(SteamVentBlockEntity.class);
    }

    @Nullable
    public static String bearingRpm(BlockEntity be) {
        return be instanceof PropellerBearingBlockEntity bearing ? String.format(Locale.US, "%,.0f RPM", Math.abs(bearing.getRotationSpeed())) : null;
    }

    @Nullable
    public static Float bearingSpeedFraction(BlockEntity be) {
        return be instanceof PropellerBearingBlockEntity bearing ? GaugeMetric.over(Math.abs(bearing.getRotationSpeed()), 256.0F) : null;
    }

    @Nullable
    public static String bearingThrust(BlockEntity be) {
        return be instanceof PropellerBearingBlockEntity bearing ? String.format(Locale.US, "%,.1f", Math.abs(bearing.getThrust())) : null;
    }

    @Nullable
    public static String bearingAirflow(BlockEntity be) {
        return be instanceof PropellerBearingBlockEntity bearing ? String.format(Locale.US, "%,.1f", Math.abs(bearing.getAirflow())) : null;
    }

    @Nullable
    public static String hotAirOutput(BlockEntity be) {
        return be instanceof HotAirBurnerBlockEntity burner ? String.format(Locale.US, "%,.0f", burner.getGasOutput()) : null;
    }

    @Nullable
    public static Float hotAirFraction(BlockEntity be) {
        return be instanceof HotAirBurnerBlockEntity burner
            ? GaugeMetric.over((float)burner.getGasOutput(), ((Integer)AeroConfig.server().blocks.hotAirBurnerMaxHotAir.get()).intValue())
            : null;
    }

    @Nullable
    public static String steamOutput(BlockEntity be) {
        return be instanceof SteamVentBlockEntity vent ? String.format(Locale.US, "%,.0f", vent.getGasOutput()) : null;
    }

    @Nullable
    public static Float steamFraction(BlockEntity be) {
        return be instanceof SteamVentBlockEntity vent
            ? GaugeMetric.over((float)vent.getGasOutput(), ((Integer)AeroConfig.server().blocks.steamVentMaxHotAir.get()).intValue())
            : null;
    }
}
