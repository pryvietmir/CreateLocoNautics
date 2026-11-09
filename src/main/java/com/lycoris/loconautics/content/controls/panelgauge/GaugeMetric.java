package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.compat.gauge.AeronauticsGauges;
import com.lycoris.loconautics.compat.gauge.DieselGauges;
import com.lycoris.loconautics.compat.gauge.PropulsionGauges;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineTuning;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity;
import com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity;
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public enum GaugeMetric implements StringRepresentable {
    TEMPERATURE(FireboxBlockEntity.class, FireboxBlockEntity::formattedTemperature, FireboxBlockEntity::temperatureFraction, ChatFormatting.GOLD, 27),
    PRESSURE(FireboxBlockEntity.class, be -> pct(be.getDisplayPressure()), FireboxBlockEntity::getDisplayPressure, ChatFormatting.AQUA, 21),
    STEAM_OUTPUT(
        FireboxBlockEntity.class,
        be -> String.format(Locale.US, "%,.0f SU", be.getDisplaySu()),
        be -> over(be.getDisplaySu(), be.getDisplayMaxSu()),
        ChatFormatting.AQUA,
        15
    ),
    SHAFT_RPM(
        FireboxBlockEntity.class,
        be -> String.format(Locale.US, "%,.0f RPM", be.getDisplayRpm()),
        be -> over(be.getDisplayRpm(), (float)((Double)LoconauticsConfig.TAKEOFF_MAX_RPM.get()).doubleValue()),
        ChatFormatting.AQUA,
        9
    ),
    BURN_RATE(
        FireboxBlockEntity.class,
        be -> String.format(Locale.US, "%.2fx", be.getLastBurnRate()),
        be -> over(be.getLastBurnRate(), 4.0F),
        ChatFormatting.GOLD,
        22
    ),
    FUEL_REMAINING(
        FireboxBlockEntity.class,
        be -> FireboxBlockEntity.formatTime(be.getDisplayRemaining()),
        be -> over(be.getDisplayRemaining(), 600.0F),
        ChatFormatting.WHITE,
        32
    ),
    WATER_LEVEL(
        FireboxBlockEntity.class,
        GaugeMetric::waterLevel,
        be -> be.isDisplayValid() ? over(be.getDisplayWater(), be.getWaterCapacity()) : 0.0F,
        ChatFormatting.BLUE,
        2
    ),
    EFFICIENCY(FireboxBlockEntity.class, be -> pct(be.getDisplayEfficiency()), FireboxBlockEntity::getDisplayEfficiency, ChatFormatting.GREEN, 17),
    NETWORK_STRESS(FireboxBlockEntity.class, be -> pct(be.getLastStressRatio()), FireboxBlockEntity::getLastStressRatio, ChatFormatting.AQUA, 23),
    TRAIN_WEIGHT(FireboxBlockEntity.class, GaugeMetric::trainWeight, be -> over((float)Math.max(0.0, be.getLastTrainMass()), 3000.0F), ChatFormatting.GOLD, 3),
    SPEEDOMETER_RPM(
        SpeedGaugeBlockEntity.class,
        be -> String.format(Locale.US, "%,.0f RPM", Math.abs(be.getSpeed())),
        be -> SpeedGaugeBlockEntity.getDialTarget(be.getSpeed()),
        ChatFormatting.AQUA,
        9
    ),
    STRESSOMETER_LOAD_SU(
        StressGaugeBlockEntity.class,
        be -> String.format(Locale.US, "%,.0f/%,.0f SU", be.getNetworkStress(), be.getNetworkCapacity()),
        be -> over(be.getNetworkStress(), be.getNetworkCapacity()),
        ChatFormatting.AQUA,
        23
    ),
    STRESSOMETER_LOAD(
        StressGaugeBlockEntity.class,
        be -> pct(over(be.getNetworkStress(), be.getNetworkCapacity())),
        be -> over(be.getNetworkStress(), be.getNetworkCapacity()),
        ChatFormatting.AQUA,
        22
    ),
    STRESSOMETER_SPARE(
        StressGaugeBlockEntity.class,
        be -> String.format(
            Locale.US,
            "%,.0f SU (%s)",
            Math.max(0.0F, be.getNetworkCapacity() - be.getNetworkStress()),
            pct(over(Math.max(0.0F, be.getNetworkCapacity() - be.getNetworkStress()), be.getNetworkCapacity()))
        ),
        be -> over(Math.max(0.0F, be.getNetworkCapacity() - be.getNetworkStress()), be.getNetworkCapacity()),
        ChatFormatting.GREEN,
        17
    ),
    SUB_LEVEL_SPEED(VelocitySensorBlockEntity.class, be -> speed(be, 1.0, "m/s"), GaugeMetric::speedFraction, ChatFormatting.AQUA, 9),
    SUB_LEVEL_SPEED_FPS(VelocitySensorBlockEntity.class, be -> speed(be, 3.28084, "ft/s"), GaugeMetric::speedFraction, ChatFormatting.AQUA, 9),
    SUB_LEVEL_SPEED_KMH(VelocitySensorBlockEntity.class, be -> speed(be, 3.6, "km/h"), GaugeMetric::speedFraction, ChatFormatting.AQUA, 9),
    SUB_LEVEL_SPEED_MPH(VelocitySensorBlockEntity.class, be -> speed(be, 2.23694, "mph"), GaugeMetric::speedFraction, ChatFormatting.AQUA, 9),
    ALTITUDE_HEIGHT(
        AltitudeSensorBlockEntity.class,
        be -> String.format(Locale.US, "%,.1f m", be.getWorldHeight()),
        AltitudeSensorBlockEntity::getNormalHeight,
        ChatFormatting.AQUA,
        9
    ),
    ALTITUDE_AIR_PRESSURE(
        AltitudeSensorBlockEntity.class,
        be -> String.format(Locale.US, "%.2f%%", be.getAirPressure() * 100.0),
        be -> (float)be.getAirPressure(),
        ChatFormatting.WHITE,
        21
    ),
    GIMBAL_X_ANGLE(GimbalSensorBlockEntity.class, be -> degrees(be.getXAngle()), be -> angleFraction(be.getXAngle()), ChatFormatting.RED, 9),
    GIMBAL_Z_ANGLE(GimbalSensorBlockEntity.class, be -> degrees(be.getZAngle()), be -> angleFraction(be.getZAngle()), ChatFormatting.BLUE, 9),
    FLUID_VALVE_STATE(
        FluidValveBlockEntity.class,
        be -> Component.translatable(valveOpen(be) ? "loconautics.gui.goggles.gauge_valve_open" : "loconautics.gui.goggles.gauge_valve_closed").getString(),
        be -> valveOpen(be) ? 1.0F : 0.0F,
        ChatFormatting.AQUA,
        9
    ),
    ENGINE_RPM(
        PortableEngineBlockEntity.class,
        be -> String.format(Locale.US, "%,.0f RPM", Math.abs(be.getGeneratedSpeed())),
        be -> over(Math.abs(be.getGeneratedSpeed()), 256.0F),
        ChatFormatting.AQUA,
        9
    ),
    ENGINE_FUEL(PortableEngineBlockEntity.class, GaugeMetric::engineFuel, GaugeMetric::engineFuelFraction, ChatFormatting.WHITE, 32),
    TANK_FLUID(FluidTankBlockEntity.class, GaugeMetric::tankFluid, GaugeMetric::tankFraction, ChatFormatting.BLUE, 2),
    TANK_FILL(FluidTankBlockEntity.class, be -> pct(tankFraction(be)), GaugeMetric::tankFraction, ChatFormatting.BLUE, 2),
    DIESEL_FUEL(GaugeMetric.Group.DIESEL_ENGINE, be -> DieselGauges.fuel(be), be -> DieselGauges.fuelFraction(be), ChatFormatting.GOLD, 32),
    DIESEL_FILL(GaugeMetric.Group.DIESEL_ENGINE, be -> DieselGauges.fuelFill(be), be -> DieselGauges.fuelFraction(be), ChatFormatting.GOLD, 32),
    DIESEL_RPM(GaugeMetric.Group.DIESEL_ENGINE, be -> DieselGauges.rpm(be), be -> DieselGauges.rpmFraction(be), ChatFormatting.AQUA, 9),
    DIESEL_THROTTLE(GaugeMetric.Group.DIESEL_ENGINE, be -> DieselGauges.throttle(be), be -> DieselGauges.throttleFraction(be), ChatFormatting.GREEN, 17),
    DIESEL_BURNS_FOR(GaugeMetric.Group.DIESEL_ENGINE, be -> DieselGauges.burnsFor(be), be -> DieselGauges.burnsForFraction(be), ChatFormatting.WHITE, 32),
    THRUSTER_FUEL(GaugeMetric.Group.THRUSTER, be -> PropulsionGauges.thrusterFuel(be), be -> PropulsionGauges.thrusterFuelFraction(be), ChatFormatting.GOLD, 32),
    THRUSTER_THROTTLE(GaugeMetric.Group.THRUSTER, be -> PropulsionGauges.thrusterThrottle(be), be -> PropulsionGauges.thrusterPower(be), ChatFormatting.GREEN, 17),
    THRUSTER_THRUST(GaugeMetric.Group.THRUSTER, be -> PropulsionGauges.thrusterThrust(be), be -> PropulsionGauges.thrusterPower(be), ChatFormatting.AQUA, 9),
    THRUSTER_ION_ENERGY(GaugeMetric.Group.THRUSTER, be -> PropulsionGauges.ionEnergy(be), be -> PropulsionGauges.ionEnergyFraction(be), ChatFormatting.AQUA, 23),
    BURNER_FUEL(GaugeMetric.Group.LIQUID_BURNER, be -> PropulsionGauges.burnerFuel(be), be -> PropulsionGauges.burnerFuelFraction(be), ChatFormatting.GOLD, 32),
    BURNER_TIME(GaugeMetric.Group.LIQUID_BURNER, be -> PropulsionGauges.burnerTime(be), be -> PropulsionGauges.burnerTimeFraction(be), ChatFormatting.WHITE, 32),
    BURNER_HEAT(GaugeMetric.Group.LIQUID_BURNER, be -> PropulsionGauges.burnerHeat(be), be -> PropulsionGauges.burnerHeatFraction(be), ChatFormatting.GOLD, 22),
    PROPELLER_RPM(GaugeMetric.Group.PROPELLER_BEARING, AeronauticsGauges::bearingRpm, AeronauticsGauges::bearingSpeedFraction, ChatFormatting.AQUA, 9),
    PROPELLER_THRUST(GaugeMetric.Group.PROPELLER_BEARING, AeronauticsGauges::bearingThrust, AeronauticsGauges::bearingSpeedFraction, ChatFormatting.AQUA, 9),
    PROPELLER_AIRFLOW(GaugeMetric.Group.PROPELLER_BEARING, AeronauticsGauges::bearingAirflow, AeronauticsGauges::bearingSpeedFraction, ChatFormatting.WHITE, 21),
    HOT_AIR_OUTPUT(GaugeMetric.Group.HOT_AIR_BURNER, AeronauticsGauges::hotAirOutput, AeronauticsGauges::hotAirFraction, ChatFormatting.GOLD, 22),
    STEAM_VENT_OUTPUT(GaugeMetric.Group.STEAM_VENT, AeronauticsGauges::steamOutput, AeronauticsGauges::steamFraction, ChatFormatting.WHITE, 21),
    SMALL_ENGINE_RPM(
        SmallEngineBlockEntity.class,
        be -> String.format(Locale.US, "%,.0f RPM", be.getOutputRpm()),
        be -> over(be.getOutputRpm(), 256.0F),
        ChatFormatting.AQUA,
        9
    ),
    SMALL_ENGINE_OUTPUT(
        SmallEngineBlockEntity.class,
        be -> String.format(Locale.US, "%,.0f SU", be.getOutputSu()),
        be -> over(be.getOutputSu(), 24576.0F),
        ChatFormatting.AQUA,
        15
    ),
    SMALL_ENGINE_THROTTLE(
        SmallEngineBlockEntity.class,
        be -> SmallEngineTuning.isOff(be.getControlSignal()) ? "0%" : pct(SmallEngineTuning.speedFraction(be.getControlSignal())),
        be -> SmallEngineTuning.speedFraction(be.getControlSignal()),
        ChatFormatting.GREEN,
        17
    ),
    SMALL_ENGINE_FUEL(
        SmallEngineBlockEntity.class,
        be -> String.format(Locale.US, "%,d / %,d mB", be.getFuelAmount(), be.getFuelCapacity()),
        be -> over(be.getFuelAmount(), be.getFuelCapacity()),
        ChatFormatting.GOLD,
        32
    ),
    SMALL_ENGINE_FILL(
        SmallEngineBlockEntity.class,
        be -> pct(over(be.getFuelAmount(), be.getFuelCapacity())),
        be -> over(be.getFuelAmount(), be.getFuelCapacity()),
        ChatFormatting.GOLD,
        32
    ),
    SMALL_ENGINE_BURNS_FOR(
        SmallEngineBlockEntity.class,
        GaugeMetric::smallEngineBurnsFor,
        be -> be.getBurnsForSeconds() < 0 ? 0.0F : over(be.getBurnsForSeconds(), 600.0F),
        ChatFormatting.WHITE,
        32
    );

    public static final Codec<GaugeMetric> CODEC = StringRepresentable.fromValues(GaugeMetric::values);
    public static final StreamCodec<ByteBuf, GaugeMetric> STREAM_CODEC = ByteBufCodecs.idMapper(GaugeMetric::byOrdinal, Enum::ordinal);
    private final GaugeMetric.Group group;
    private final Function<BlockEntity, String> reader;
    private final Function<BlockEntity, Float> normalizer;
    private final ChatFormatting color;
    private final int defaultBackground;

    public static GaugeMetric byOrdinal(int ordinal) {
        GaugeMetric[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : TEMPERATURE;
    }

    <S extends BlockEntity> GaugeMetric(Class<S> source, Function<S, String> reader, Function<S, Float> normalizer, ChatFormatting color, int defaultBackground) {
        this.group = GaugeMetric.Group.declaring(source);
        this.reader = from(source, reader);
        this.normalizer = from(source, normalizer);
        this.color = color;
        this.defaultBackground = defaultBackground;
    }

    GaugeMetric(
        GaugeMetric.Group group, Function<BlockEntity, String> reader, Function<BlockEntity, Float> normalizer, ChatFormatting color, int defaultBackground
    ) {
        this.group = group;
        this.reader = reader;
        this.normalizer = normalizer;
        this.color = color;
        this.defaultBackground = defaultBackground;
    }

    private static <S extends BlockEntity, T> Function<BlockEntity, T> from(Class<S> source, Function<S, T> reader) {
        return be -> source.isInstance(be) ? reader.apply(source.cast(be)) : null;
    }

    private static boolean valveOpen(FluidValveBlockEntity be) {
        BlockState state = be.getBlockState();
        return state.hasProperty(FluidValveBlock.ENABLED) && (Boolean)state.getValue(FluidValveBlock.ENABLED);
    }

    private static String smallEngineBurnsFor(SmallEngineBlockEntity be) {
        int seconds = be.getBurnsForSeconds();
        return seconds < 0 ? Component.translatable("loconautics.gui.goggles.gauge_small_engine_idle").getString() : FireboxBlockEntity.formatTime(seconds);
    }

    private static String engineFuel(PortableEngineBlockEntity be) {
        return be.isCurrentFuelInfinite() ? "∞" : FireboxBlockEntity.formatTime(be.getCurrentBurnTime() / 20);
    }

    private static float engineFuelFraction(PortableEngineBlockEntity be) {
        return be.isCurrentFuelInfinite() ? 1.0F : over(be.getCurrentBurnTime(), be.getTotalBurnTime());
    }

    private static String tankFluid(FluidTankBlockEntity be) {
        FluidTankBlockEntity controller = be.getControllerBE();
        return controller == null
            ? null
            : String.format(Locale.US, "%,d / %,d mB", controller.getTankInventory().getFluidAmount(), controller.getTankInventory().getCapacity());
    }

    private static float tankFraction(FluidTankBlockEntity be) {
        FluidTankBlockEntity controller = be.getControllerBE();
        return controller == null ? 0.0F : over(controller.getTankInventory().getFluidAmount(), controller.getTankInventory().getCapacity());
    }

    private static String speed(VelocitySensorBlockEntity be, double factor, String unit) {
        return String.format(Locale.US, "%.1f %s", Math.abs(be.getAdjustedVelocity()) * factor, unit);
    }

    private static float speedFraction(VelocitySensorBlockEntity be) {
        return over(Math.abs(be.getAdjustedVelocity()), be.getMaxSpeed().getValue());
    }

    private static String degrees(double radians) {
        return String.format(Locale.US, "%.1f°", Math.toDegrees(radians));
    }

    private static float angleFraction(double radians) {
        return (float)((Math.toDegrees(radians) + 90.0) / 180.0);
    }

    public GaugeMetric.Group group() {
        return this.group;
    }

    public boolean accepts(BlockEntity source) {
        return source != null && this.reader.apply(source) != null;
    }

    public static GaugeMetric[] valuesOf(GaugeMetric.Group group) {
        List<GaugeMetric> list = new ArrayList<>();

        for (GaugeMetric metric : values()) {
            if (metric.group == group) {
                list.add(metric);
            }
        }

        return list.toArray(new GaugeMetric[0]);
    }

    public static GaugeMetric defaultFor(GaugeMetric.Group group) {
        return valuesOf(group)[0];
    }

    public int defaultBackground() {
        return this.defaultBackground;
    }

    public float normalized(BlockEntity source) {
        if (source == null) {
            return 0.0F;
        }

        Float value = this.normalizer.apply(source);
        return value != null && !Float.isNaN(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    public static float over(float value, float fullScale) {
        return fullScale <= 0.0F ? 0.0F : value / fullScale;
    }

    public String read(BlockEntity source) {
        if (source == null) {
            return "--";
        }

        String value = this.reader.apply(source);
        return value == null ? "--" : value;
    }

    public ChatFormatting color() {
        return this.color;
    }

    public ChatFormatting colorFor(BlockEntity source) {
        return this.color;
    }

    public String langKey() {
        return "gui.goggles.gauge_" + this.getSerializedName();
    }

    private static String waterLevel(FireboxBlockEntity be) {
        return !be.isDisplayValid()
            ? Component.translatable("loconautics.gui.goggles.gauge_boiler_missing").getString()
            : String.format(Locale.US, "%,d / %,d mB", be.getDisplayWater(), be.getWaterCapacity());
    }

    public boolean faulted(BlockEntity source) {
        return this == WATER_LEVEL && source instanceof FireboxBlockEntity be && !be.isDisplayValid();
    }

    private static String trainWeight(FireboxBlockEntity be) {
        return !be.onATrain()
            ? Component.translatable("loconautics.gui.goggles.gauge_not_on_train").getString()
            : String.format(Locale.US, "%,.0f", be.getLastTrainMass());
    }

    public static GaugeMetric byName(String name) {
        for (GaugeMetric metric : values()) {
            if (metric.getSerializedName().equals(name)) {
                return metric;
            }
        }

        return TEMPERATURE;
    }

    public static String pct(float fraction) {
        return (int)(fraction * 100.0F) + "%";
    }

    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public enum Group {
        FIREBOX("loconautics", "Loconautics", "Firebox", FireboxBlockEntity.class),
        SPEEDOMETER("create", "Create", "Speedometer", SpeedGaugeBlockEntity.class),
        STRESSOMETER("create", "Create", "Stressometer", StressGaugeBlockEntity.class),
        VELOCITY("simulated", "Create: Simulated", "Velocity Sensor", VelocitySensorBlockEntity.class),
        ALTITUDE("simulated", "Create: Simulated", "Altitude Sensor", AltitudeSensorBlockEntity.class),
        GIMBAL("simulated", "Create: Simulated", "Gimbal Sensor", GimbalSensorBlockEntity.class),
        SMALL_ENGINE("loconautics", "Loconautics", "Small Combustion Engine", SmallEngineBlockEntity.class),
        FLUID_VALVE("create", "Create", "Fluid Valve", FluidValveBlockEntity.class),
        PORTABLE_ENGINE("simulated", "Create: Simulated", "Portable Engine", PortableEngineBlockEntity.class),
        FLUID_TANK("create", "Create", "Fluid Tank", FluidTankBlockEntity.class),
        DIESEL_ENGINE("createdieselgenerators", "Create: Diesel Generators", "Diesel Engines", () -> DieselGauges.sources()),
        THRUSTER("createpropulsion", "Create: Propulsion", "Thrusters", () -> PropulsionGauges.thrusterSources()),
        LIQUID_BURNER("createpropulsion", "Create: Propulsion", "Liquid Burner", () -> PropulsionGauges.burnerSources()),
        PROPELLER_BEARING("aeronautics", "Create: Aeronautics", "Propeller Bearing", AeronauticsGauges::bearingSources),
        HOT_AIR_BURNER("aeronautics", "Create: Aeronautics", "Hot Air Burner", AeronauticsGauges::hotAirSources),
        STEAM_VENT("aeronautics", "Create: Aeronautics", "Steam Vent", AeronauticsGauges::steamSources);

        private final String modId;
        private final String modName;
        private final String blockName;
        private final Supplier<List<Class<? extends BlockEntity>>> sourceList;
        private List<Class<? extends BlockEntity>> sources;

        Group(String modId, String modName, String blockName, Class<? extends BlockEntity> source) {
            this(modId, modName, blockName, () -> List.of(source));
        }

        Group(String modId, String modName, String blockName, Supplier<List<Class<? extends BlockEntity>>> sourceList) {
            this.modId = modId;
            this.modName = modName;
            this.blockName = blockName;
            this.sourceList = sourceList;
        }

        public String modId() {
            return this.modId;
        }

        public String modName() {
            return this.modName;
        }

        public String blockName() {
            return this.blockName;
        }

        private List<Class<? extends BlockEntity>> sources() {
            if (this.sources == null) {
                this.sources = ModList.get().isLoaded(this.modId) ? this.sourceList.get() : List.of();
            }

            return this.sources;
        }

        @Nullable
        public static GaugeMetric.Group of(@Nullable BlockEntity source) {
            if (source == null) {
                return null;
            }

            for (GaugeMetric.Group group : values()) {
                for (Class<? extends BlockEntity> type : group.sources()) {
                    if (type.isInstance(source)) {
                        return group;
                    }
                }
            }

            return null;
        }

        static GaugeMetric.Group declaring(Class<? extends BlockEntity> source) {
            for (GaugeMetric.Group group : values()) {
                if (group.sources().contains(source)) {
                    return group;
                }
            }

            throw new IllegalArgumentException("no panelgauge group declares " + source);
        }
    }
}
