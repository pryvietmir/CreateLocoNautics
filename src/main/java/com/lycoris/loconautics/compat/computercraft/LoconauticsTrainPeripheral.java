package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import com.lycoris.loconautics.content.schedule.RouteDistance;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.schedule.SchedulePathfinder;
import com.lycoris.loconautics.content.schedule.SchedulePlanner;
import com.lycoris.loconautics.content.schedule.ScheduleProgressStore;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.door.TrainDoorsAccessor;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.TrackAhead;
import com.lycoris.loconautics.trainphysics.signal.TrainSignalAccessor;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.storage.CarriageStorage;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.NotAttachedException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class LoconauticsTrainPeripheral implements IPeripheral, CcEvents.Target {
    private static final Map<BearingAxleBlockEntity, LoconauticsTrainPeripheral> CACHE = new WeakHashMap<>();
    private final BearingAxleBlockEntity axle;
    private final Map<String, LoconauticsTrainPeripheral.FrequencySlot> frequencyLinks = new HashMap<>();
    private final Set<IComputerAccess> attached = new HashSet<>();
    private UUID claimedTrain;
    private static final double ON_TRACK_REACH = 1.5;
    private static final double CURVE_SAMPLE_STEP = 0.25;

    private LoconauticsTrainPeripheral(BearingAxleBlockEntity axle) {
        this.axle = axle;
    }

    public static LoconauticsTrainPeripheral of(BearingAxleBlockEntity axle) {
        return CACHE.computeIfAbsent(axle, LoconauticsTrainPeripheral::new);
    }

    public String getType() {
        return "loconautics:bearing_axle";
    }

    public boolean equals(IPeripheral other) {
        return other instanceof LoconauticsTrainPeripheral p && p.axle == this.axle;
    }

    public void attach(IComputerAccess computer) {
        this.attached.add(computer);
        CcEvents.register(this);
    }

    public void detach(IComputerAccess computer) {
        this.attached.remove(computer);
        if (this.attached.isEmpty()) {
            CcEvents.unregister(this);
            this.releaseEverything();
        }
    }

    @Override
    public UUID watchedTrain() {
        return this.trainIdOrNull();
    }

    @Override
    public void queue(String event, Object[] arguments) {
        for (IComputerAccess computer : List.copyOf(this.attached)) {
            try {
                computer.queueEvent(event, arguments);
            } catch (NotAttachedException gone) {
                this.attached.remove(computer);
            }
        }
    }

    public void releaseEverything() {
        for (LoconauticsTrainPeripheral.FrequencySlot slot : this.frequencyLinks.values()) {
            slot.receiver().removeFromNetwork();
            slot.transmitter().removeFromNetwork();
        }

        this.frequencyLinks.clear();
        UUID id = this.trainIdOrNull();
        LoconauticsConstants.LOGGER
            .debug("[ccsteer] release resolved={} claimed={} held={}", new Object[]{id, this.claimedTrain, id != null && CcControl.held(id)});
        if (id != null) {
            CcControl.release(id);
        }

        if (this.claimedTrain != null) {
            CcControl.release(this.claimedTrain);
            this.claimedTrain = null;
        }
    }

    UUID trainIdOrNull() {
        try {
            return this.train().id();
        } catch (LuaException ignored) {
            return null;
        }
    }

    private SableTrain train() throws LuaException {
        ServerLevel level = (ServerLevel)this.axle.getLevel();
        if (level == null) {
            throw new LuaException("the bearing axle is not loaded in any level");
        } else {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                throw new LuaException("this level has no sub levels, Sable is not tracking anything here");
            } else {
                SubLevel sub = Sable.HELPER.getContaining(level, this.axle.getBlockPos());
                if (sub == null) {
                    throw new LuaException("the bearing axle is not in a sub level, the carriage has to be assembled");
                } else {
                    UUID subId = sub.getUniqueId();
                    SableTrain train = SableTrainRegistry.bySubLevel(subId);
                    if (train == null) {
                        throw new LuaException("this carriage is not on rails as a train, place it on a track first");
                    } else {
                        return train;
                    }
                }
            }
        }
    }

    void requireLive() throws LuaException {
        this.train();
    }

    private SableScheduleRuntime schedule(SableTrain train) {
        return SableScheduleRuntime.forConsist(train);
    }

    private ServerSubLevel resolveSubLevel(SableTrain train) {
        return SubLevelData.resolve(train.level(), train.car().subLevelId());
    }

    @LuaFunction(mainThread = true)
    public String getId() throws LuaException {
        return this.train().id().toString();
    }

    @LuaFunction(mainThread = true)
    public String getName() throws LuaException {
        SableTrain train = this.train();
        return SableTrainSpawner.trainName(train.level(), train);
    }

    @LuaFunction(mainThread = true)
    public void setName(String name) throws LuaException {
        SableTrain train = this.train();
        SableTrainSpawner.nameConsist(train.level(), train, name == null ? "" : name);
    }

    @LuaFunction(mainThread = true)
    public int getCarriageCount() throws LuaException {
        SableTrain train = this.train();
        return ConsistAccess.carriages(train.level(), train.car().subLevelId()).size();
    }

    @LuaFunction(mainThread = true)
    public double getSpeed() throws LuaException {
        return this.train().speed();
    }

    @LuaFunction(mainThread = true)
    public double getTargetSpeed() throws LuaException {
        return this.train().targetSpeed();
    }

    @LuaFunction(mainThread = true)
    public double getAccel() throws LuaException {
        return this.train().measuredAccel();
    }

    @LuaFunction(mainThread = true)
    public double getAccelLimit() throws LuaException {
        SableTrain train = this.train();
        LoconauticsConstants.LOGGER
            .debug(
                "[ccaccel] limit={} speed={} target={} hold={} braked={} derailed={} physics={} mass={}",
                new Object[]{
                    train.accel(),
                    train.speed(),
                    train.targetSpeed(),
                    train.brakeHold(),
                    train.isBraked(),
                    train.isDerailed(),
                    train.isPhysics(),
                    TrainConsist.totalMass(train.level(), train.car().subLevelId())
                }
            );
        return train.accel();
    }

    @LuaFunction(mainThread = true)
    public double getMass() throws LuaException {
        SableTrain train = this.train();
        return TrainConsist.totalMass(train.level(), train.car().subLevelId());
    }

    @LuaFunction(mainThread = true)
    public double getStressRatio() throws LuaException {
        return this.axle.getNetworkStressRatio();
    }

    @LuaFunction(mainThread = true)
    public double getSlopeAngle() throws LuaException {
        return Math.abs(this.getPitch());
    }

    @LuaFunction(mainThread = true)
    public boolean isPowered() throws LuaException {
        return this.train().isPowered();
    }

    @LuaFunction(mainThread = true)
    public boolean isRailed() throws LuaException {
        return !this.train().isDerailed();
    }

    @LuaFunction(mainThread = true)
    public boolean isDerailed() throws LuaException {
        return this.train().isDerailed();
    }

    @LuaFunction(mainThread = true)
    public boolean isBraked() throws LuaException {
        return this.train().isBraked();
    }

    @LuaFunction(mainThread = true)
    public boolean isBrakeHard() throws LuaException {
        return this.train().brakeHard();
    }

    @LuaFunction(mainThread = true)
    public boolean isScheduleHeld() throws LuaException {
        return this.train().scheduleHold();
    }

    @LuaFunction(mainThread = true)
    public int getSteerInput() throws LuaException {
        CcControl control = CcControl.get(this.train().id());
        Integer steer = control == null ? null : control.steer();
        return steer != null ? steer : this.axle.getSteerInput();
    }

    @LuaFunction(mainThread = true)
    public boolean isAtStation() throws LuaException {
        return this.train().isAtStation();
    }

    @LuaFunction(mainThread = true)
    public String getStationState() throws LuaException {
        return switch (this.train().stationState()) {
            case RUNNING -> "running";
            case STOPPING -> "stopping";
            case STOPPED -> "stopped";
            case DEPARTING -> "departing";
        };
    }

    @LuaFunction(mainThread = true)
    public String getStationName() throws LuaException {
        SableTrain train = this.train();
        GlobalStation s = train.atStation() != null ? train.atStation() : train.currentStation();
        return s != null ? s.name : null;
    }

    @LuaFunction(mainThread = true)
    public String getTargetStationName() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        if (rt == null) {
            return null;
        }

        GlobalStation t = rt.targetStation();
        return t != null ? t.name : null;
    }

    @LuaFunction(mainThread = true)
    public Double getDisToStation(IArguments args) throws LuaException {
        SableTrain train = this.train();
        String stationName = (String)args.optString(0).orElse(null);
        if (stationName != null && !stationName.isEmpty()) {
            GlobalStation target = this.findStation(stationName);
            return this.pathDistance(train, SchedulePlanner.locationOf(this.noseGraph(train), target));
        } else {
            return this.nearestStationDistance(train);
        }
    }

    private Double nearestStationDistance(SableTrain train) throws LuaException {
        TrackGraph graph = this.noseGraph(train);
        Double best = null;

        for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
            Double distance;
            try {
                distance = this.pathDistance(train, SchedulePlanner.locationOf(graph, station));
            } catch (LuaException unreachable) {
                continue;
            }

            if (distance != null && (best == null || distance < best)) {
                best = distance;
            }
        }

        return best;
    }

    @LuaFunction(mainThread = true)
    public int getDwellTicks() throws LuaException {
        return this.train().dwellTicks();
    }

    @LuaFunction(mainThread = true)
    public Double getGapToRed() throws LuaException {
        double gap = TrainSignalAccessor.gapToRed(this.train());
        return Double.isNaN(gap) ? null : gap;
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextSignal() throws LuaException {
        TrainSignalAccessor.SignalAhead ahead = TrainSignalAccessor.nextSignal(this.train());
        return ahead == null ? null : CcNetwork.nextSignal(ahead);
    }

    @LuaFunction(mainThread = true)
    public boolean isHeldAtRed() throws LuaException {
        return TrainSignalAccessor.isHeldAtRed(this.train());
    }

    private TrackAhead.Report trackAhead(IArguments args, int rangeIndex) throws LuaException {
        return TrackAhead.scan(this.train(), CcTrackAhead.range(args, rangeIndex));
    }

    @LuaFunction(mainThread = true)
    public String getTrackType() throws LuaException {
        TrackAhead.Report report = TrackAhead.scan(this.train(), 1.0);
        return report == null ? null : CcTrackAhead.typeOf(report.startHost());
    }

    @LuaFunction(mainThread = true)
    public String getTrackSubLevelId() throws LuaException {
        TrackAhead.Report report = TrackAhead.scan(this.train(), 1.0);
        return report != null && report.startHost() != null ? report.startHost().toString() : null;
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getTrackEnd(IArguments args) throws LuaException {
        return CcTrackAhead.end(this.trackAhead(args, 0));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToTrackEnd(IArguments args) throws LuaException {
        return CcTrackAhead.distance(this.getTrackEnd(args));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextCurve(IArguments args) throws LuaException {
        TrackAhead.Report report = this.trackAhead(args, 0);
        return report == null ? null : CcTrackAhead.curve(TrackAhead.nextCurve(report));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToCurve(IArguments args) throws LuaException {
        return CcTrackAhead.distance(this.getNextCurve(args));
    }

    @LuaFunction(mainThread = true)
    public Double getSlopeAhead(IArguments args) throws LuaException {
        double distance = args.getDouble(0);
        if (!(distance < 0.0) && !(distance > 1024.0)) {
            TrackAhead.Report report = TrackAhead.scan(this.train(), Math.max(distance, 1.0));
            Double slope = report == null ? null : TrackAhead.slopeAt(report, distance);
            return slope == null ? null : hundredths(slope);
        } else {
            throw new LuaException("distance must be between 0 and 1024 blocks");
        }
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextSlopeChange(IArguments args) throws LuaException {
        double minDelta = args.optDouble(0).orElse(1.0);
        if (minDelta <= 0.0) {
            throw new LuaException("minDelta must be above 0 degrees");
        }

        TrackAhead.Report report = this.trackAhead(args, 1);
        return report == null ? null : CcTrackAhead.slopeChange(TrackAhead.nextSlopeChange(report, minDelta));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToSlopeChange(IArguments args) throws LuaException {
        return CcTrackAhead.distance(this.getNextSlopeChange(args));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextTrackChange(IArguments args) throws LuaException {
        TrackAhead.Report report = this.trackAhead(args, 0);
        return report == null ? null : CcTrackAhead.change(TrackAhead.firstChange(report));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextSubLevelTrack(IArguments args) throws LuaException {
        TrackAhead.Report report = this.trackAhead(args, 0);
        return report == null ? null : CcTrackAhead.change(TrackAhead.nextChange(report, true));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToSubLevelTrack(IArguments args) throws LuaException {
        return CcTrackAhead.distance(this.getNextSubLevelTrack(args));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getNextWorldTrack(IArguments args) throws LuaException {
        TrackAhead.Report report = this.trackAhead(args, 0);
        return report == null ? null : CcTrackAhead.change(TrackAhead.nextChange(report, false));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToWorldTrack(IArguments args) throws LuaException {
        return CcTrackAhead.distance(this.getNextWorldTrack(args));
    }

    @LuaFunction(mainThread = true)
    public Double getDisToSignal(IArguments args) throws LuaException {
        SableTrain train = this.train();
        String state = (String)args.optString(0).orElse(null);
        double gap;
        if (state != null && !state.isEmpty()) {
            if (!state.equals("red")) {
                throw new LuaException("state must be \"red\" or omitted for any signal");
            }

            gap = TrainSignalAccessor.gapToRed(train);
        } else {
            gap = TrainSignalAccessor.gapToAnySignal(train);
        }

        return Double.isNaN(gap) ? null : gap;
    }

    @LuaFunction(mainThread = true)
    public boolean doorsOpen() throws LuaException {
        return this.train().doorsOpen();
    }

    @LuaFunction(mainThread = true)
    public void setDoors(boolean open) throws LuaException {
        SableTrain train = this.train();
        this.control(train).setDoors(open);
        TrainDoorsAccessor.setDoors(train, open);
    }

    @LuaFunction(mainThread = true)
    public void releaseDoors() throws LuaException {
        CcControl control = CcControl.get(this.train().id());
        if (control != null) {
            control.setDoors(null);
        }
    }

    @LuaFunction(mainThread = true)
    public double getYaw() throws LuaException {
        return hundredths(SubLevelData.yawDeg(this.railOrientation(), this.axleForward()));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getWheelLift() throws LuaException {
        SableTrain train = this.train();
        ServerSubLevel sub = this.resolveSubLevel(train);
        CarriageRoll.Roll roll = CarriageRoll.rollOf(sub);
        Map<String, Object> out = new HashMap<>();
        boolean rolling = roll != null && roll.rolling;
        out.put("rolling", rolling);
        out.put("lock", roll == null ? "none" : roll.lock);
        out.put("rollDegrees", rolling ? hundredths(Math.toDegrees(roll.phi)) : 0.0);
        out.put("tipDegrees", rolling ? hundredths(Math.toDegrees(roll.tip)) : 0.0);
        out.put("ungluesAtDegrees", rolling ? hundredths(Math.toDegrees(roll.unglue)) : 0.0);
        out.put("liftDegrees", rolling ? hundredths(Math.abs(Math.toDegrees(roll.phi))) : 0.0);
        out.put("holdDegrees", hundredths(Math.toDegrees(CarriageRoll.holdAngle())));
        out.put("predictedLiftDegrees", rolling ? hundredths(Math.toDegrees(roll.predicted)) : 0.0);
        out.put("side", rolling && !(Math.abs(roll.phi) < 0.001) ? (leansRight(train, sub, roll.phi) ? "right" : "left") : "none");
        out.put("suspensionDegrees", rolling ? hundredths(Math.toDegrees(roll.suspension)) : 0.0);
        out.put("couplerTorque", rolling ? hundredths(roll.couplerTorque) : 0.0);
        out.put("couplerTwistDegrees", rolling ? hundredths(Math.toDegrees(roll.couplerTwist)) : 0.0);
        out.put("liftedWheels", rolling ? roll.lifted : 0);
        out.put("wheels", rolling ? roll.y.length : 0);
        return out;
    }

    private static boolean leansRight(SableTrain train, ServerSubLevel sub, double phi) {
        Vec3 nose = SableTrainDriver.axleNose(train);
        Vector3d[] ref = nose != null && sub != null ? TrainRailAdvance.bogeyReference(sub, train.car()) : null;
        boolean facesAgainstChord = ref != null && nose.x * ref[1].x + nose.y * ref[1].y + nose.z * ref[1].z < 0.0;
        return phi > 0.0 != facesAgainstChord;
    }

    @LuaFunction(mainThread = true)
    public double getPitch() throws LuaException {
        return hundredths(SubLevelData.pitchDeg(this.railOrientation(), this.axleForward()));
    }

    @LuaFunction(mainThread = true)
    public double getRoll() throws LuaException {
        return hundredths(SubLevelData.rollDeg(this.railOrientation(), this.axleForward()));
    }

    private Quaterniond railOrientation() throws LuaException {
        SableTrain train = this.train();
        ServerSubLevel sub = this.resolveSubLevel(train);
        if (sub == null) {
            throw new LuaException("sub level not available");
        }

        RailCarriage rail = train.car().carriage();
        Quaterniond tweak = train.poseTweak().local(rail == null ? null : rail.referenceForward());
        return new Quaterniond(SubLevelData.orientation(sub)).mul(tweak.invert());
    }

    private Vector3d axleForward() {
        Direction front = BearingAxleBlock.front(this.axle.getBlockState());
        return new Vector3d(front.getStepX(), 0.0, front.getStepZ());
    }

    private static double hundredths(double degrees) {
        return Math.round(degrees * 100.0) / 100.0;
    }

    @LuaFunction(mainThread = true)
    public boolean isUpright(double minDot) throws LuaException {
        ServerSubLevel sub = this.resolveSubLevel(this.train());
        if (sub == null) {
            throw new LuaException("sub level not available");
        } else {
            return SubLevelData.isUpright(sub, minDot);
        }
    }

    @LuaFunction(mainThread = true)
    public double getBodyRoll() throws LuaException {
        return this.train().poseTweak().targetRoll();
    }

    @LuaFunction(mainThread = true)
    public void setBodyRoll(double degrees) throws LuaException {
        this.train().poseTweak().setRoll(bodyAngle("roll", degrees));
    }

    @LuaFunction(mainThread = true)
    public double getBodyPitch() throws LuaException {
        return this.train().poseTweak().targetPitch();
    }

    @LuaFunction(mainThread = true)
    public void setBodyPitch(double degrees) throws LuaException {
        this.train().poseTweak().setPitch(bodyAngle("pitch", degrees));
    }

    @LuaFunction(mainThread = true)
    public double getBodyYaw() throws LuaException {
        return this.train().poseTweak().targetYaw();
    }

    @LuaFunction(mainThread = true)
    public void setBodyYaw(double degrees) throws LuaException {
        this.train().poseTweak().setYaw(bodyAngle("yaw", degrees));
    }

    @LuaFunction(mainThread = true)
    public double getBodyX() throws LuaException {
        return this.train().poseTweak().targetX();
    }

    @LuaFunction(mainThread = true)
    public void setBodyX(double blocks) throws LuaException {
        PoseTweak tweak = this.train().poseTweak();
        tweak.setOffset(bodyOffset("x", blocks), tweak.targetY(), tweak.targetZ());
    }

    @LuaFunction(mainThread = true)
    public double getBodyY() throws LuaException {
        return this.train().poseTweak().targetY();
    }

    @LuaFunction(mainThread = true)
    public void setBodyY(double blocks) throws LuaException {
        PoseTweak tweak = this.train().poseTweak();
        tweak.setOffset(tweak.targetX(), bodyOffset("y", blocks), tweak.targetZ());
    }

    @LuaFunction(mainThread = true)
    public double getBodyZ() throws LuaException {
        return this.train().poseTweak().targetZ();
    }

    @LuaFunction(mainThread = true)
    public void setBodyZ(double blocks) throws LuaException {
        PoseTweak tweak = this.train().poseTweak();
        tweak.setOffset(tweak.targetX(), tweak.targetY(), bodyOffset("z", blocks));
    }

    @LuaFunction(mainThread = true)
    public void setBodyOffset(double x, double y, double z) throws LuaException {
        this.train().poseTweak().setOffset(bodyOffset("x", x), bodyOffset("y", y), bodyOffset("z", z));
    }

    @LuaFunction(mainThread = true)
    public void setBodyLerpSpeed(double speed) throws LuaException {
        this.train().poseTweak().setLerpSpeed(bodyRange("speed", speed, 0.0, 1.0));
    }

    private static double bodyAngle(String name, double degrees) throws LuaException {
        return bodyRange(name, degrees, -360.0, 360.0);
    }

    private static double bodyOffset(String name, double blocks) throws LuaException {
        return bodyRange(name, blocks, -32.0, 32.0);
    }

    private static double bodyRange(String name, double value, double min, double max) throws LuaException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new LuaException(name + " must be a real number");
        } else if (!(value < min) && !(value > max)) {
            return value;
        } else {
            throw new LuaException(String.format("%s must be between %s and %s", name, trim(min), trim(max)));
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long)value) : String.valueOf(value);
    }

    @LuaFunction(mainThread = true)
    public double getBodyLerpSpeed() throws LuaException {
        return this.train().poseTweak().lerpSpeed();
    }

    @LuaFunction(mainThread = true)
    public boolean isBodySettled() throws LuaException {
        return this.train().poseTweak().isSettled();
    }

    @LuaFunction(mainThread = true)
    public void resetBody() throws LuaException {
        this.train().poseTweak().reset();
    }

    @LuaFunction(mainThread = true)
    public Map<String, Double> getVelocity() throws LuaException {
        ServerSubLevel sub = this.resolveSubLevel(this.train());
        if (sub == null) {
            throw new LuaException("sub level not available");
        } else {
            Vector3d vel = SubLevelData.linearVelocity(sub);
            if (vel == null) {
                throw new LuaException("velocity not available, body may not be simulated");
            } else {
                return Map.of("x", vel.x, "y", vel.y, "z", vel.z);
            }
        }
    }

    @LuaFunction(mainThread = true)
    public Map<String, Double> getWorldPos() throws LuaException {
        ServerSubLevel sub = this.resolveSubLevel(this.train());
        if (sub == null) {
            throw new LuaException("sub level not available");
        }

        Vector3d pos = SubLevelData.position(sub);
        return Map.of("x", pos.x, "y", pos.y, "z", pos.z);
    }

    @LuaFunction(mainThread = true)
    public List<Map<String, Double>> getWorldPosOf(String blockId) throws LuaException {
        SableTrain train = this.train();
        List<Map<String, Double>> results = new ArrayList<>();

        for (UUID id : ConsistAccess.carriages(train.level(), train.car().subLevelId())) {
            ServerSubLevel sub = SubLevelData.resolve(train.level(), id);
            if (sub != null) {
                for (BlockEntity be : SubLevelBlocks.blockEntities(sub)) {
                    String beId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                    if (beId.equals(blockId)) {
                        Vector3d world = SubLevelData.worldOfBlock(sub, be.getBlockPos());
                        results.add(Map.of("x", world.x, "y", world.y, "z", world.z));
                    }
                }
            }
        }

        return results;
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getDrivetrain() throws LuaException {
        return CcDrivetrain.report(CcDrivetrain.check(this.axle.getLevel(), this.axle.getBlockPos()));
    }

    @LuaFunction(mainThread = true)
    public TransmissionPeripheral getTransmission() throws LuaException {
        LocomotiveHardware.Drivetrain drivetrain = CcDrivetrain.check(this.axle.getLevel(), this.axle.getBlockPos());
        if (drivetrain.transmission() == null) {
            throw new LuaException(CcDrivetrain.describe(drivetrain.problem()));
        } else {
            return TransmissionPeripheral.of(drivetrain.transmission());
        }
    }

    @LuaFunction(mainThread = true)
    public double getTargetRpm() throws LuaException {
        return this.getTransmission().getTargetRpm();
    }

    private void requireDrivetrain() throws LuaException {
        CcDrivetrain.require(this.axle.getLevel(), this.axle.getBlockPos());
    }

    @LuaFunction(mainThread = true)
    public void setManualControl(boolean enabled) throws LuaException {
        SableTrain train = this.train();
        if (enabled) {
            this.requireDrivetrain();
            this.claimedTrain = train.id();
            CcControl.claimClean(train.id()).setTargetSpeed(train.targetSpeed());
            TrainAdvancements.aiTakeover(train);
        } else {
            CcControl.release(train.id());
        }
    }

    @LuaFunction(mainThread = true)
    public void release() throws LuaException {
        this.releaseEverything();
    }

    @LuaFunction(mainThread = true)
    public boolean isManualControl() throws LuaException {
        return CcControl.held(this.train().id());
    }

    @LuaFunction(mainThread = true)
    public void setTargetSpeed(double speed) throws LuaException {
        SableTrain train = this.train();
        CcControl control = held(train);
        this.requireDrivetrain();
        control.setTargetSpeed(speed);
    }

    @LuaFunction(mainThread = true)
    public void setAccel(double accel) throws LuaException {
        held(this.train()).setAccel(Math.abs(accel));
    }

    @LuaFunction(mainThread = true)
    public void setBrakeScale(double scale) throws LuaException {
        if (!(scale < 0.0) && !(scale > 1.0)) {
            held(this.train()).setBrakeScale(scale);
        } else {
            throw new LuaException("brake scale must be 0 to 1");
        }
    }

    @LuaFunction(mainThread = true)
    public int stop(IArguments args) throws LuaException {
        SableTrain train = this.train();
        return this.control(train).stopAt(train, args.optBoolean(0).orElse(false), Double.NaN);
    }

    @LuaFunction(mainThread = true)
    public int stopIn(IArguments args) throws LuaException {
        double distance = args.getDouble(0);
        if (!(distance > 0.0)) {
            throw new LuaException("distance must be more than 0 blocks");
        }

        SableTrain train = this.train();
        return this.control(train).stopAt(train, false, distance);
    }

    @LuaFunction(mainThread = true)
    public boolean isStopping() throws LuaException {
        CcControl control = CcControl.get(this.train().id());
        return control != null && control.isStopping();
    }

    @LuaFunction(mainThread = true)
    public double getBrakingDistance() throws LuaException {
        SableTrain train = this.train();
        double speed = Math.abs(train.speed());
        return speed * speed / (2.0 * StationLanding.decelerationFor(train));
    }

    @LuaFunction(mainThread = true)
    public double getEmergencyBrakingDistance() throws LuaException {
        return StationLanding.hardStopDistance(this.train());
    }

    @LuaFunction(mainThread = true)
    public double getServiceDecel() throws LuaException {
        return StationLanding.decelerationFor(this.train());
    }

    @LuaFunction(mainThread = true)
    public double getEmergencyDecel() throws LuaException {
        return StationLanding.emergencyDecel();
    }

    @LuaFunction(mainThread = true)
    public double getSlope() throws LuaException {
        double direction = this.train().speed() < -1.0E-4 ? -1.0 : 1.0;
        return -this.getPitch() * direction;
    }

    @LuaFunction(mainThread = true)
    public void steer(int dir) throws LuaException {
        if (dir >= -1 && dir <= 1) {
            LoconauticsConstants.LOGGER.debug("[ccsteer] steer {} on {}", dir, this.trainIdOrNull());
            held(this.train()).setSteer(dir);
        } else {
            throw new LuaException("steer dir must be -1, 0, or 1");
        }
    }

    @LuaFunction(mainThread = true)
    public String getScheduleState() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        if (rt == null) {
            return null;
        }

        return switch (rt.state) {
            case PRE_TRANSIT -> "pre_transit";
            case IN_TRANSIT -> "in_transit";
            case TURNTABLE -> "turntable";
            case POST_TRANSIT -> "post_transit";
        };
    }

    @LuaFunction(mainThread = true)
    public int getScheduleEntry() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        return rt == null ? 0 : rt.currentEntry + 1;
    }

    @LuaFunction(mainThread = true)
    public int getScheduleSize() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        return rt != null && rt.schedule != null ? rt.schedule.entries.size() : 0;
    }

    @LuaFunction(mainThread = true)
    public double getRemainingToTarget() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        return rt == null ? -1.0 : rt.remainingToTarget();
    }

    @LuaFunction(mainThread = true)
    public boolean isPaused() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        return rt != null && rt.paused;
    }

    @LuaFunction(mainThread = true)
    public boolean isCompleted() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        return rt != null && rt.completed;
    }

    @LuaFunction(mainThread = true)
    public String getHeldReason() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        return rt == null ? null : rt.heldReason();
    }

    @LuaFunction(mainThread = true)
    public void setSchedule(IArguments args) throws LuaException {
        Map<?, ?> table = args.getTable(0);
        SableTrain train = this.train();
        ServerSubLevel sub = this.resolveSubLevel(train);
        if (sub == null) {
            throw new LuaException("sub level not available");
        }

        AnalogControllerBlockEntity controller = SubLevelBlocks.find(sub, AnalogControllerBlockEntity.class);
        if (controller == null) {
            throw new LuaException("this cart has no analog controller, a schedule has nothing to drive");
        }

        LocomotiveHardware.Result hardware = LocomotiveHardware.validate(sub, controller);
        if (!hardware.ok()) {
            throw new LuaException(hardware.messageKey());
        }

        Schedule schedule = CcSchedule.fromLua(train.level().registryAccess(), table);
        UUID key = SableScheduleRuntime.consistKey(train.id());
        ScheduleProgressStore.get(train.level().getServer()).putSchedule(key, schedule.write(train.level().registryAccess()));
        SableScheduleRuntime.assign(key, schedule);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getSchedule() throws LuaException {
        SableScheduleRuntime rt = this.schedule(this.train());
        if (rt != null && rt.schedule != null) {
            return CcSchedule.toLua(rt.schedule);
        } else {
            throw new LuaException("this train has no schedule, call setSchedule first");
        }
    }

    @LuaFunction(mainThread = true)
    public void clearSchedule() throws LuaException {
        SableTrain train = this.train();
        UUID key = SableScheduleRuntime.consistKey(train.id());
        SableScheduleRuntime.clear(key);
        ScheduleProgressStore.get(train.level().getServer()).remove(key);
    }

    @LuaFunction(mainThread = true)
    public void pauseSchedule() throws LuaException {
        this.running(this.train()).paused = true;
    }

    @LuaFunction(mainThread = true)
    public void resumeSchedule() throws LuaException {
        SableScheduleRuntime rt = this.running(this.train());
        rt.paused = false;
        rt.state = SableScheduleRuntime.State.PRE_TRANSIT;
    }

    @LuaFunction(mainThread = true)
    public Double getDisToCoord(double x, double y, double z) throws LuaException {
        SableTrain train = this.train();
        LoconauticsTrainPeripheral.EdgeTarget target = this.nearestEdgeTarget(this.noseGraph(train), x, y, z);
        return this.pathDistance(train, target.location());
    }

    @LuaFunction(mainThread = true)
    public List<String> getStepsToStation(String stationName) throws LuaException {
        SableTrain train = this.train();
        GlobalStation target = this.findStation(stationName);
        Couple<TrackNode> nodes = this.stationNodes(train, target);
        return this.stepsTo(train, (TrackNode)nodes.getFirst(), (TrackNode)nodes.getSecond());
    }

    @LuaFunction(mainThread = true)
    public List<String> getStepsToCoord(double x, double y, double z) throws LuaException {
        SableTrain train = this.train();
        Couple<TrackNode> target = this.nearestEdgeNodes(this.noseGraph(train), x, y, z);
        return this.stepsTo(train, (TrackNode)target.getFirst(), (TrackNode)target.getSecond());
    }

    @LuaFunction(mainThread = true)
    public boolean parkAtStation(String stationName) throws LuaException {
        SableTrain train = this.train();
        this.requireDrivetrain();
        GlobalStation station = this.findStation(stationName);
        TrackGraph graph = this.noseGraph(train);
        SchedulePath path = SchedulePlanner.planTo(graph, this.nosePoint(train), SchedulePlanner.locationOf(graph, station), station, true);
        if (path == null) {
            throw new LuaException(
                "no route from here to station \""
                    + stationName
                    + "\", the track may be broken between the two or the station only takes trains from its other side"
            );
        }

        this.control(train).parkAt(path, TrainStations.noseRail(train));
        return true;
    }

    @LuaFunction
    public MethodResult parkAtCoord(ILuaContext context, double x, double y, double z) throws LuaException {
        long taskId = context.issueMainThreadTask(() -> this.startParkAtCoord(x, y, z));
        return awaitTask(taskId);
    }

    private static MethodResult awaitTask(long taskId) {
        return MethodResult.pullEvent("task_complete", event -> {
            if (event.length < 3 || !(event[1] instanceof Number id && id.longValue() == taskId)) {
                return awaitTask(taskId);
            } else if (Boolean.TRUE.equals(event[2])) {
                return awaitPark((String)event[3], ((Number)event[4]).intValue());
            } else {
                throw new LuaException(event.length > 3 && event[3] != null ? event[3].toString() : "park failed");
            }
        });
    }

    private Object[] startParkAtCoord(double x, double y, double z) throws LuaException {
        SableTrain train = this.train();
        this.requireDrivetrain();
        TrackGraph graph = this.noseGraph(train);
        LoconauticsTrainPeripheral.EdgeTarget target = this.nearestEdgeTarget(graph, x, y, z);
        if (target.miss() > 1.5) {
            throw new LuaException(
                "there is no track at "
                    + trim(x)
                    + ", "
                    + trim(y)
                    + ", "
                    + trim(z)
                    + " on this train's track graph, the nearest rail is "
                    + Math.round(target.miss())
                    + " blocks away"
            );
        }

        SchedulePath path = SchedulePlanner.planTo(graph, this.nosePoint(train), target.location(), null, false);
        if (path == null) {
            throw new LuaException(
                "no route from here to the track nearest " + trim(x) + ", " + trim(y) + ", " + trim(z) + ", the track may be broken between the two"
            );
        }

        int parkId = this.control(train).parkAt(path, TrainStations.noseRail(train));
        return new Object[]{train.id().toString(), parkId};
    }

    private static MethodResult awaitPark(String trainId, int parkId) {
        return MethodResult.pullEvent("train_parked", event -> {
            boolean ours = event.length >= 4 && trainId.equals(event[1]) && event[3] instanceof Number id && id.intValue() == parkId;
            return ours ? MethodResult.of(Boolean.TRUE.equals(event[2])) : awaitPark(trainId, parkId);
        });
    }

    @LuaFunction(mainThread = true)
    public boolean isParking() throws LuaException {
        CcControl control = CcControl.get(this.train().id());
        return control != null && control.isParking();
    }

    @LuaFunction(mainThread = true)
    public int getItemCount(IArguments args) throws LuaException {
        return this.countItems(this.train(), -1, (String)args.optString(0).orElse(null));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Integer> getItems() throws LuaException {
        return this.collectItems(this.train(), -1);
    }

    @LuaFunction(mainThread = true)
    public int getFluidAmount(IArguments args) throws LuaException {
        return this.countFluid(this.train(), -1, (String)args.optString(0).orElse(null));
    }

    @LuaFunction(mainThread = true)
    public Map<String, Map<String, Integer>> getFluids() throws LuaException {
        return this.collectFluids(this.train(), -1);
    }

    @LuaFunction(mainThread = true)
    public int getCarriageItemCount(IArguments args) throws LuaException {
        return this.carriageItemCount(args.getInt(0), (String)args.optString(1).orElse(null));
    }

    int carriageItemCount(int index, String filterId) throws LuaException {
        SableTrain train = this.train();
        this.requireCarriage(train, index);
        return this.countItems(train, index - 1, filterId);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Integer> getCarriageItems(int index) throws LuaException {
        SableTrain train = this.train();
        this.requireCarriage(train, index);
        return this.collectItems(train, index - 1);
    }

    @LuaFunction(mainThread = true)
    public int getCarriageFluidAmount(IArguments args) throws LuaException {
        return this.carriageFluidAmount(args.getInt(0), (String)args.optString(1).orElse(null));
    }

    int carriageFluidAmount(int index, String filterId) throws LuaException {
        SableTrain train = this.train();
        this.requireCarriage(train, index);
        return this.countFluid(train, index - 1, filterId);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Map<String, Integer>> getCarriageFluids(int index) throws LuaException {
        SableTrain train = this.train();
        this.requireCarriage(train, index);
        return this.collectFluids(train, index - 1);
    }

    @LuaFunction(mainThread = true)
    public int getCarriageItemCapacity(int index) throws LuaException {
        SableTrain train = this.train();
        ServerSubLevel sub = this.requireCarriage(train, index);
        int cap = 0;

        for (IItemHandler h : CarriageStorage.items(sub)) {
            for (int slot = 0; slot < h.getSlots(); slot++) {
                cap += h.getSlotLimit(slot);
            }
        }

        return cap;
    }

    @LuaFunction(mainThread = true)
    public int getCarriageFluidCapacity(int index) throws LuaException {
        SableTrain train = this.train();
        ServerSubLevel sub = this.requireCarriage(train, index);
        int cap = 0;

        for (IFluidHandler h : CarriageStorage.fluids(sub)) {
            for (int tank = 0; tank < h.getTanks(); tank++) {
                cap += h.getTankCapacity(tank);
            }
        }

        return cap;
    }

    @LuaFunction(mainThread = true)
    public int getCarriageBlockCount(int index) throws LuaException {
        return SubLevelBlocks.countBlocks(this.requireCarriage(this.train(), index));
    }

    @LuaFunction(mainThread = true)
    public int getCarriagePassengerCount(int index) throws LuaException {
        SableTrain train = this.train();
        this.requireCarriage(train, index);
        return ConsistAccess.countPlayersAboard(train.level(), train.car().subLevelId(), index - 1);
    }

    @LuaFunction(mainThread = true)
    public boolean getCarriageHasPassengers(int index) throws LuaException {
        return this.getCarriagePassengerCount(index) > 0;
    }

    @LuaFunction(mainThread = true)
    public double getLength() throws LuaException {
        SableTrain train = this.train();
        return TrainLength.consistLength(train.level(), train.car().subLevelId());
    }

    @LuaFunction(mainThread = true)
    public double getWidth() throws LuaException {
        SableTrain train = this.train();
        return TrainLength.consistWidth(train.level(), train.car().subLevelId());
    }

    @LuaFunction(mainThread = true)
    public double getHeight() throws LuaException {
        SableTrain train = this.train();
        return TrainLength.consistHeight(train.level(), train.car().subLevelId());
    }

    private SableTrain carriageTrain(int index) throws LuaException {
        SableTrain train = this.train();
        this.requireCarriage(train, index);
        ServerSubLevel sub = ConsistAccess.carriage(train.level(), train.car().subLevelId(), index - 1);
        SableTrain car = sub == null ? null : SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (car != null && car.car() != null) {
            return car;
        } else {
            throw new LuaException("that carriage is not on a track");
        }
    }

    public double getCarriageLength(int index) throws LuaException {
        return TrainLength.carLength(this.train().level(), this.carriageTrain(index));
    }

    public double getCarriageWidth(int index) throws LuaException {
        return TrainLength.carWidth(this.train().level(), this.carriageTrain(index));
    }

    public double getCarriageHeight(int index) throws LuaException {
        return TrainLength.carHeight(this.train().level(), this.carriageTrain(index));
    }

    @LuaFunction(mainThread = true)
    public CarriageHandle getCarriage(int index) throws LuaException {
        this.requireCarriage(this.train(), index);
        return new CarriageHandle(this, index);
    }

    @LuaFunction(mainThread = true)
    public void setFrequency(String slot, String itemId1, String itemId2) throws LuaException {
        ServerLevel level = (ServerLevel)this.axle.getLevel();
        if (level == null) {
            throw new LuaException("no level");
        }

        LoconauticsTrainPeripheral.FrequencySlot existing = this.frequencyLinks.remove(slot);
        if (existing != null) {
            existing.receiver().removeFromNetwork();
            existing.transmitter().removeFromNetwork();
        }

        ItemStack first = frequencyItem(itemId1);
        ItemStack second = frequencyItem(itemId2);
        CcFrequencyLink receiver = new CcFrequencyLink(this.axle, level, first, second, true);
        CcFrequencyLink transmitter = new CcFrequencyLink(this.axle, level, first, second, false);
        receiver.addToNetwork();
        transmitter.addToNetwork();
        this.frequencyLinks.put(slot, new LoconauticsTrainPeripheral.FrequencySlot(receiver, transmitter));
    }

    @LuaFunction(mainThread = true)
    public int getFrequencyState(String slot) throws LuaException {
        return this.slot(slot).receiver().getReceivedStrength();
    }

    @LuaFunction(mainThread = true)
    public void setFrequencyState(String slot, int strength) throws LuaException {
        if (strength >= 0 && strength <= 15) {
            this.slot(slot).transmitter().setTransmitStrength(strength);
        } else {
            throw new LuaException("strength must be 0 to 15");
        }
    }

    @LuaFunction(mainThread = true)
    public void clearFrequency(String slot) throws LuaException {
        LoconauticsTrainPeripheral.FrequencySlot removed = this.frequencyLinks.remove(slot);
        if (removed != null) {
            removed.receiver().removeFromNetwork();
            removed.transmitter().removeFromNetwork();
        }
    }

    @LuaFunction(mainThread = true)
    public boolean getLeftLink() {
        return this.axle.isLeftLink();
    }

    @LuaFunction(mainThread = true)
    public boolean getRightLink() {
        return this.axle.isRightLink();
    }

    @LuaFunction(mainThread = true)
    public boolean getLeftRedstone() {
        return this.axle.isLeftRedstone();
    }

    @LuaFunction(mainThread = true)
    public boolean getRightRedstone() {
        return this.axle.isRightRedstone();
    }

    private LoconauticsTrainPeripheral.FrequencySlot slot(String name) throws LuaException {
        LoconauticsTrainPeripheral.FrequencySlot found = this.frequencyLinks.get(name);
        if (found == null) {
            throw new LuaException("slot \"" + name + "\" has no frequency bound, call setFrequency first");
        } else {
            return found;
        }
    }

    private static ItemStack frequencyItem(String id) throws LuaException {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
            return new ItemStack((ItemLike)BuiltInRegistries.ITEM.get(key));
        } else {
            throw new LuaException("unknown item id \"" + id + "\"");
        }
    }

    private int countItems(SableTrain train, int carriageIndex, String filterId) {
        return ConsistAccess.countItems(
            train.level(),
            train.car().subLevelId(),
            carriageIndex,
            stack -> filterId == null ? true : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(filterId)
        );
    }

    private int countFluid(SableTrain train, int carriageIndex, String filterId) {
        return ConsistAccess.countFluid(
            train.level(),
            train.car().subLevelId(),
            carriageIndex,
            stack -> filterId == null ? true : NeoForgeRegistries.FLUID_TYPES.getKey(stack.getFluid().getFluidType()).toString().equals(filterId)
        );
    }

    private Map<String, Integer> collectItems(SableTrain train, int carriageIndex) {
        Map<String, Integer> out = new HashMap<>();

        for (IItemHandler h : ConsistAccess.itemsIn(train.level(), train.car().subLevelId(), carriageIndex)) {
            for (int slot = 0; slot < h.getSlots(); slot++) {
                ItemStack stack = h.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    out.merge(id, stack.getCount(), Integer::sum);
                }
            }
        }

        return out;
    }

    private Map<String, Map<String, Integer>> collectFluids(SableTrain train, int carriageIndex) {
        return CarriageStorage.tallyFluids(ConsistAccess.fluidsIn(train.level(), train.car().subLevelId(), carriageIndex));
    }

    private TrackGraph noseGraph(SableTrain train) throws LuaException {
        RailCarriage carriage = TrainStations.noseRail(train);
        if (carriage == null) {
            throw new LuaException("train has no carriage, is it assembled?");
        } else {
            TrackGraph graph = carriage.graph();
            if (graph == null) {
                throw new LuaException("train is not on a track graph, is it on rails?");
            } else {
                return graph;
            }
        }
    }

    private TravellingPoint nosePoint(SableTrain train) throws LuaException {
        TravellingPoint nose = TrainStations.nosePoint(train);
        if (nose == null) {
            throw new LuaException("could not resolve train nose point");
        } else if (nose.edge == null) {
            throw new LuaException("train nose point has no edge, train may not be fully on track");
        } else {
            return nose;
        }
    }

    private GlobalStation findStation(String name) throws LuaException {
        SableTrain train = this.train();
        TrackGraph graph = this.noseGraph(train);

        for (GlobalStation s : graph.getPoints(EdgePointType.STATION)) {
            if (name.equals(s.name)) {
                return s;
            }
        }

        if (Create.RAILWAYS != null) {
            for (TrackGraph g : Create.RAILWAYS.trackNetworks.values()) {
                if (g != graph) {
                    for (GlobalStation s : g.getPoints(EdgePointType.STATION)) {
                        if (name.equals(s.name)) {
                            throw new LuaException("station \"" + name + "\" exists but is on a different track graph, the train cannot reach it");
                        }
                    }
                }
            }
        }

        throw new LuaException("station \"" + name + "\" not found in any track graph");
    }

    private Couple<TrackNode> nearestEdgeNodes(TrackGraph graph, double x, double y, double z) throws LuaException {
        LoconauticsTrainPeripheral.EdgeTarget target = this.nearestEdgeTarget(graph, x, y, z);
        return Couple.create(target.a(), target.b());
    }

    private LoconauticsTrainPeripheral.EdgeTarget nearestEdgeTarget(TrackGraph graph, double x, double y, double z) throws LuaException {
        Vec3 target = new Vec3(x, y, z);
        double bestDist = Double.MAX_VALUE;
        TrackNode bestA = null;
        TrackNode bestB = null;
        double bestPositionFromA = 0.0;

        for (TrackNodeLocation locA : graph.getNodes()) {
            TrackNode nodeA = graph.locateNode(locA);
            if (nodeA != null) {
                Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(nodeA);
                if (connections != null) {
                    for (Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                        TrackEdge edge = entry.getValue();
                        if (!edge.isInterDimensional()) {
                            double[] hit = edge.isTurn()
                                ? nearestOnCurve(graph, edge, target)
                                : nearestOnStraight(locA.getLocation(), entry.getKey().getLocation().getLocation(), edge.getLength(), target);
                            if (hit[1] < bestDist) {
                                bestDist = hit[1];
                                bestA = nodeA;
                                bestB = entry.getKey();
                                bestPositionFromA = hit[0];
                            }
                        }
                    }
                }
            }
        }

        if (bestA == null) {
            throw new LuaException("no track edges found in this graph");
        }

        TrackGraphLocation loc = new TrackGraphLocation();
        loc.graph = graph;
        loc.edge = Couple.create(bestA.getLocation(), bestB.getLocation());
        loc.position = bestPositionFromA;
        return new LoconauticsTrainPeripheral.EdgeTarget(bestA, bestB, loc, bestDist);
    }

    private static double[] nearestOnStraight(Vec3 posA, Vec3 posB, double length, Vec3 target) {
        Vec3 segment = posB.subtract(posA);
        double segLenSq = segment.lengthSqr();
        double t = segLenSq < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, target.subtract(posA).dot(segment) / segLenSq));
        return new double[]{t * length, posA.add(segment.scale(t)).distanceTo(target)};
    }

    private static double[] nearestOnCurve(TrackGraph graph, TrackEdge edge, Vec3 target) {
        double length = edge.getLength();
        double t = 0.0;
        double along = 0.0;
        double[] best = new double[]{0.0, edge.getPosition(graph, 0.0).distanceTo(target)};

        while (along < length) {
            double step = Math.min(0.25, length - along);
            t = Math.min(1.0, edge.incrementT(t, step));
            along += step;
            double d = edge.getPosition(graph, t).distanceTo(target);
            if (d < best[1]) {
                best[0] = along;
                best[1] = d;
            }
        }

        return best;
    }

    private Double pathDistance(SableTrain train, TrackGraphLocation target) throws LuaException {
        TrackGraph graph = this.noseGraph(train);
        TravellingPoint nose = this.nosePoint(train);
        TrackNode targetA = graph.locateNode((TrackNodeLocation)target.edge.getFirst());
        TrackNode targetB = graph.locateNode((TrackNodeLocation)target.edge.getSecond());
        if (targetA == null || targetB == null) {
            throw new LuaException("target is on a rail that is not part of this train's track graph");
        }

        if (RouteDistance.onTargetEdge(nose, target)) {
            double signed = RouteDistance.signedOnEdge(nose, target);
            if (signed * SableTrainDriver.noseSense(train, nose) >= 0.0) {
                return Math.abs(signed);
            } else {
                throw new LuaException("no route ahead of the train, the target is behind it on the same rail");
            }
        } else {
            SchedulePathfinder.Path path = SchedulePathfinder.findAhead(graph, nose, targetA, targetB, SableTrainDriver.noseSense(train, nose));
            if (path == null) {
                throw new LuaException("no route ahead of the train, it may be facing away or on a disconnected segment");
            } else {
                RouteDistance.Measure measure = RouteDistance.measure(nose, graph, path.edges(), target);
                if (!measure.known()) {
                    throw new LuaException("could not measure distance along the route: " + measure.reason());
                } else {
                    return measure.distance();
                }
            }
        }
    }

    private List<String> stepsTo(SableTrain train, TrackNode targetA, TrackNode targetB) throws LuaException {
        TrackGraph graph = this.noseGraph(train);
        TravellingPoint nose = this.nosePoint(train);
        List<String> steps = new ArrayList<>();
        boolean sameRail = nose.node1 == targetA && nose.node2 == targetB || nose.node1 == targetB && nose.node2 == targetA;
        if (sameRail) {
            return steps;
        }

        SchedulePathfinder.Path path = SchedulePathfinder.findAhead(graph, nose, targetA, targetB, SableTrainDriver.noseSense(train, nose));
        if (path == null) {
            throw new LuaException("no route ahead of the train, it may be facing away or on a disconnected segment");
        }

        List<Couple<TrackNode>> edges = path.edges();

        for (int i = 0; i < edges.size() - 1; i++) {
            TrackNode pivot = (TrackNode)edges.get(i).getSecond();
            Map<TrackNode, ?> branches = graph.getConnectionsFrom(pivot);
            if (branches != null && branches.size() > 2) {
                Vec3 entryDir = this.hopDir(edges.get(i));
                Vec3 exitDir = this.hopDir(edges.get(i + 1));
                if (entryDir != null && exitDir != null) {
                    double cross = entryDir.x * exitDir.z - entryDir.z * exitDir.x;
                    steps.add(Math.abs(cross) < 0.3 ? "straight" : (cross > 0.0 ? "right" : "left"));
                }
            }
        }

        return steps;
    }

    private Couple<TrackNode> stationNodes(SableTrain train, GlobalStation target) throws LuaException {
        TrackGraph graph = this.noseGraph(train);
        TrackNode first = graph.locateNode((TrackNodeLocation)target.edgeLocation.getFirst());
        TrackNode second = graph.locateNode((TrackNodeLocation)target.edgeLocation.getSecond());
        if (first != null && second != null) {
            return Couple.create(first, second);
        } else {
            throw new LuaException("station \"" + target.name + "\" sits on a rail that is not part of this train's track graph");
        }
    }

    private Vec3 hopDir(Couple<TrackNode> hop) {
        Vec3 from = ((TrackNode)hop.getFirst()).getLocation().getLocation();
        Vec3 to = ((TrackNode)hop.getSecond()).getLocation().getLocation();
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        return len < 1.0E-6 ? null : new Vec3(dx / len, 0.0, dz / len);
    }

    private ServerSubLevel requireCarriage(SableTrain train, int index) throws LuaException {
        ServerSubLevel sub = ConsistAccess.carriage(train.level(), train.car().subLevelId(), index - 1);
        if (sub == null) {
            int count = ConsistAccess.carriages(train.level(), train.car().subLevelId()).size();
            throw new LuaException("carriage " + index + " not found, this train has " + count + (count == 1 ? " carriage" : " carriages"));
        } else {
            return sub;
        }
    }

    private SableScheduleRuntime running(SableTrain train) throws LuaException {
        SableScheduleRuntime rt = this.schedule(train);
        if (rt == null) {
            throw new LuaException("this train has no schedule, call setSchedule first");
        } else {
            return rt;
        }
    }

    private CcControl control(SableTrain train) {
        this.claimedTrain = train.id();
        return CcControl.claim(train.id());
    }

    private static CcControl held(SableTrain train) throws LuaException {
        CcControl control = CcControl.get(train.id());
        if (control == null) {
            throw new LuaException("manual control is not enabled, call setManualControl(true) first");
        } else {
            return control;
        }
    }

    private record EdgeTarget(TrackNode a, TrackNode b, TrackGraphLocation location, double miss) {
    }

    private record FrequencySlot(CcFrequencyLink receiver, CcFrequencyLink transmitter) {
    }
}
