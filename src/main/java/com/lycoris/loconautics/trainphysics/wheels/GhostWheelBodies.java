package com.lycoris.loconautics.trainphysics.wheels;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.SubLevelAssemblyUtil;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.spawn.WheelColliders;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.visual.BogeyYawVisual;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public final class GhostWheelBodies {
    public static final boolean ENABLED = true;
    private static final double YAW_LIMIT = Math.toRadians(12.5);
    private static final double PITCH_LIMIT = Math.toRadians(12.5);
    private static final double ROLL_LIMIT = Math.toRadians(5.0);
    private static final double DANGLE_DAMPING = 4.0;
    private static final List<ConstraintJointAxis> LOCKED_AXES = List.of(
        ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z
    );
    private static final ConstraintJointAxis[] SWING_AXES = new ConstraintJointAxis[]{
        ConstraintJointAxis.ANGULAR_X, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z
    };
    private static final Set<GhostWheelBodies.Key> DANGLING = new HashSet<>();
    private static final Map<GhostWheelBodies.Key, GhostWheelBodies.Rig> RIGS = new HashMap<>();
    private static final Set<GhostWheelBodies.Key> SEEN = new HashSet<>();
    private static final Set<UUID> WHEEL_BODIES = new HashSet<>();
    private static final Map<UUID, Integer> SETTLING = new HashMap<>();
    private static final Map<UUID, ServerLevel> ARRIVED = new LinkedHashMap<>();
    private static final int SETTLE_TICKS = 2;
    private static final double ALIGN_STEP = 0.25;
    private static final double ALIGN_DONE_DEGREES = 4.0;
    private static final Map<GhostWheelBodies.Key, Double> ALIGNING = new HashMap<>();
    private static final Map<UUID, ServerLevel> PENDING_WIPE = new LinkedHashMap<>();
    private static final double PIVOT_SLIP_LIMIT = 0.35;

    private GhostWheelBodies() {
    }

    public static void clear() {
        RIGS.clear();
        DANGLING.clear();
        ALIGNING.clear();
        SEEN.clear();
        WHEEL_BODIES.clear();
        SETTLING.clear();
        ARRIVED.clear();
        PENDING_WIPE.clear();
    }

    public static boolean isWheelBody(UUID subLevelId) {
        return WHEEL_BODIES.contains(subLevelId);
    }

    public static List<UUID> wheelBodiesOf(UUID carSubId) {
        List<UUID> bodies = new ArrayList<>();

        for (Entry<GhostWheelBodies.Key, GhostWheelBodies.Rig> entry : RIGS.entrySet()) {
            if (entry.getKey().carSub().equals(carSubId)) {
                bodies.add(entry.getValue().wheelSub());
            }
        }

        return bodies;
    }

    public static UUID parentCarOf(UUID wheelSubId) {
        for (Entry<GhostWheelBodies.Key, GhostWheelBodies.Rig> entry : RIGS.entrySet()) {
            if (entry.getValue().wheelSub().equals(wheelSubId)) {
                return entry.getKey().carSub();
            }
        }

        return null;
    }

    public static boolean isRailedPlate(UUID wheelSubId) {
        for (Entry<GhostWheelBodies.Key, GhostWheelBodies.Rig> entry : RIGS.entrySet()) {
            if (entry.getValue().wheelSub().equals(wheelSubId)) {
                return !DANGLING.contains(entry.getKey());
            }
        }

        return false;
    }

    public static boolean isWheelBodyAt(ServerLevel level, BlockPos pos) {
        return level != null && pos != null
            ? SableCompanion.INSTANCE.getContaining(level, pos.getX() >> 4, pos.getZ() >> 4) instanceof ServerSubLevel sub && isWheelBody(sub.getUniqueId())
            : false;
    }

    public static void noteSubLevelAdded(SubLevel subLevel) {
        if (subLevel instanceof ServerSubLevel sub) {
            ARRIVED.put(sub.getUniqueId(), sub.getLevel());
        }
    }

    private static void drainPendingWipes() {
        if (!PENDING_WIPE.isEmpty()) {
            Map<UUID, ServerLevel> due = Map.copyOf(PENDING_WIPE);
            PENDING_WIPE.clear();

            for (Entry<UUID, ServerLevel> entry : due.entrySet()) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(entry.getValue());
                if (container != null && container.getSubLevel(entry.getKey()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                    wipe(container, sub);
                }

                WHEEL_BODIES.remove(entry.getKey());
            }
        }
    }

    private static void purgeArrivals() {
        if (!ARRIVED.isEmpty()) {
            for (Entry<UUID, ServerLevel> entry : Map.copyOf(ARRIVED).entrySet()) {
                if (!WHEEL_BODIES.contains(entry.getKey())) {
                    ServerSubLevelContainer container = SubLevelContainer.getContainer(entry.getValue());
                    if (container != null
                        && container.getSubLevel(entry.getKey()) instanceof ServerSubLevel sub
                        && !sub.isRemoved()
                        && !PortalTwins.isTwin(sub)
                        && isOnlyWheelPads(sub)) {
                        wipe(container, sub);
                        LoconauticsConstants.LOGGER.debug("[ghostwheel] wiped orphaned wheel body {} that loaded with no bogey", entry.getKey());
                    }
                }
            }

            ARRIVED.clear();
        }
    }

    private static void wipe(ServerSubLevelContainer container, ServerSubLevel sub) {
        sub.getPlot().destroyAllBlocks();
        container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
    }

    public static void purgeSaved(ServerLevel level) {
        if (level != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                int wiped = 0;

                for (SubLevelAccess access : List.copyOf(container.getAllSubLevels())) {
                    if (access instanceof ServerSubLevel sub
                        && !sub.isRemoved()
                        && !WHEEL_BODIES.contains(sub.getUniqueId())
                        && !PortalTwins.isTwin(sub)
                        && isOnlyWheelPads(sub)) {
                        wipe(container, sub);
                        wiped++;
                    }
                }

                if (wiped > 0) {
                    LoconauticsConstants.LOGGER.debug("[ghostwheel] wiped {} orphaned wheel body sub level(s) left over from the last session", wiped);
                }
            }
        }
    }

    private static boolean isOnlyWheelPads(ServerSubLevel sub) {
        boolean anyPad = false;

        for (PlotChunkHolder holder : sub.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            LevelChunkSection[] sections = chunk.getSections();

            for (int si = 0; si < chunk.getSectionsCount(); si++) {
                LevelChunkSection section = sections[si];
                if (section != null && !section.hasOnlyAir()) {
                    for (int bx = 0; bx < 16; bx++) {
                        for (int by = 0; by < 16; by++) {
                            for (int bz = 0; bz < 16; bz++) {
                                BlockState state = section.getBlockState(bx, by, bz);
                                if (!state.isAir()) {
                                    if (!WheelColliders.isPad(state)) {
                                        return false;
                                    }

                                    anyPad = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return anyPad;
    }

    public static void sync(SableTrain train) {
        ServerSubLevel carSub = resolveSub(train);
        SableTrain.Car car = train.car();
        if (carSub != null && car != null) {
            if (!settling(train)) {
                SubLevelAssemblyUtil.anchorRotationPoint(carSub);
                UUID carId = carSub.getUniqueId();
                ServerLevel level = carSub.getLevel();

                for (SableTrain.Bogey bogey : car.bogeys()) {
                    GhostWheelBodies.Key key = new GhostWheelBodies.Key(carId, bogey.localPos());
                    BlockPos bogeyPos = carSub.getPlot().getCenterBlock().offset(bogey.localPos()).immutable();
                    if (level.getBlockState(bogeyPos).getBlock() instanceof AbstractBogeyBlock) {
                        SEEN.add(key);
                        GhostWheelBodies.Rig existing = RIGS.get(key);
                        if (existing != null && (!existing.constraint().isValid() || !wheelAlive(level, existing))) {
                            destroy(level, existing);
                            RIGS.remove(key);
                            DANGLING.remove(key);
                            ALIGNING.remove(key);
                            existing = null;
                        }

                        if (existing == null) {
                            attach(carSub, train, bogey, key);
                            existing = RIGS.get(key);
                        }

                        if (existing != null) {
                            setDangling(carSub, train, key, existing, train.isDerailed() || train.isBogeyOffTrack(bogey.localPos()));
                        }
                    }
                }
            }
        }
    }

    public static String rigState(SableTrain train, BlockPos bogeyLocal) {
        ServerSubLevel carSub = resolveSub(train);
        if (carSub == null) {
            return "nosub";
        }

        GhostWheelBodies.Key key = new GhostWheelBodies.Key(carSub.getUniqueId(), bogeyLocal);
        GhostWheelBodies.Rig rig = RIGS.get(key);
        return rig == null
            ? "norig"
            : "valid="
                + rig.constraint().isValid()
                + ",wheel="
                + wheelAlive(carSub.getLevel(), rig)
                + ",dangle="
                + DANGLING.contains(key)
                + ",align="
                + ALIGNING.get(key);
    }

    private static boolean wheelAlive(ServerLevel level, GhostWheelBodies.Rig rig) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? true : container.getSubLevel(rig.wheelSub()) instanceof ServerSubLevel wheel && !wheel.isRemoved();
    }

    private static boolean settling(SableTrain train) {
        Integer left = SETTLING.get(train.id());
        if (left == null) {
            return false;
        }

        if (left <= 1) {
            SETTLING.remove(train.id());
        } else {
            SETTLING.put(train.id(), left - 1);
        }

        return true;
    }

    public static void settleWheels(SableTrain train) {
        forEachPlate(
            train,
            (level, wheel) -> SableTeleportation.writePose(
                level, wheel, new Vector3d(wheel.logicalPose().position()), new Quaterniond(wheel.logicalPose().orientation())
            )
        );
    }

    public static void carryWheels(SableTrain train, Vector3d fromPos, Quaterniond fromQ, Vector3d toPos, Quaterniond toQ) {
        Quaterniond turn = new Quaterniond(toQ).mul(new Quaterniond(fromQ).conjugate()).normalize();
        forEachPlate(train, (level, wheel) -> {
            Vector3d local = new Quaterniond(fromQ).conjugate().transform(new Vector3d(wheel.logicalPose().position()).sub(fromPos));
            Vector3d moved = new Quaterniond(toQ).transform(local).add(toPos);
            Quaterniond turned = new Quaterniond(turn).mul(wheel.logicalPose().orientation()).normalize();
            if (Double.isFinite(moved.x) && Double.isFinite(moved.y) && Double.isFinite(moved.z)) {
                SableTeleportation.writePose(level, wheel, moved, turned);
            }
        });
    }

    public static void holdPivots(ServerSubLevel carSub, SableTrain train) {
        if (train.car() != null) {
            ServerLevel level = carSub.getLevel();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
                UUID carId = carSub.getUniqueId();

                for (SableTrain.Bogey bogey : train.car().bogeys()) {
                    GhostWheelBodies.Rig rig = RIGS.get(new GhostWheelBodies.Key(carId, bogey.localPos()));
                    if (rig != null && container.getSubLevel(rig.wheelSub()) instanceof ServerSubLevel wheel && !wheel.isRemoved()) {
                        Vector3d anchor = carSub.logicalPose().transformPosition(new Vector3d(pivotFor(carSub, train, rig.pivotInCar())), new Vector3d());
                        BlockPos wheelPlot = wheel.getPlot().getCenterBlock();
                        Vector3d held = wheel.logicalPose()
                            .transformPosition(new Vector3d(wheelPlot.getX() + 0.5, wheelPlot.getY() + 0.5, wheelPlot.getZ() + 0.5), new Vector3d());
                        Vector3d slip = anchor.sub(held);
                        if (slip.isFinite() && !(slip.lengthSquared() < 0.12249999999999998)) {
                            wheel.logicalPose().position().add(slip);
                            pipeline.teleport(wheel, wheel.logicalPose().position(), wheel.logicalPose().orientation());
                            pipeline.wakeUp(wheel);
                            wheel.updateLastPose();
                        }
                    }
                }
            }
        }
    }

    private static void forEachPlate(SableTrain train, BiConsumer<ServerLevel, ServerSubLevel> action) {
        ServerSubLevel carSub = resolveSub(train);
        if (carSub != null && train.car() != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(carSub.getLevel());
            if (container != null) {
                UUID carId = carSub.getUniqueId();

                for (SableTrain.Bogey bogey : train.car().bogeys()) {
                    GhostWheelBodies.Rig rig = RIGS.get(new GhostWheelBodies.Key(carId, bogey.localPos()));
                    if (rig != null && container.getSubLevel(rig.wheelSub()) instanceof ServerSubLevel wheel && !wheel.isRemoved()) {
                        action.accept(carSub.getLevel(), wheel);
                    }
                }
            }
        }
    }

    private static void setDangling(ServerSubLevel carSub, SableTrain train, GhostWheelBodies.Key key, GhostWheelBodies.Rig rig, boolean dangle) {
        if (dangle != DANGLING.contains(key)) {
            if (rig.constraint().isValid()) {
                if (dangle) {
                    openDangleCone(carSub, train, rig);
                    DANGLING.add(key);
                } else {
                    for (ConstraintJointAxis axis : SWING_AXES) {
                        rig.constraint().setLimit(axis, 0.0, 0.0);
                    }

                    DANGLING.remove(key);
                    ALIGNING.remove(key);
                }
            }
        }
    }

    private static void openDangleCone(ServerSubLevel carSub, SableTrain train, GhostWheelBodies.Rig rig) {
        rig.constraint().setFrame1(pivotFor(carSub, train, rig.pivotInCar()), new Quaterniond(rig.carRest()).conjugate().mul(rig.restChord()).normalize());
        rig.constraint().setLimit(ConstraintJointAxis.ANGULAR_X, -PITCH_LIMIT, PITCH_LIMIT);
        rig.constraint().setLimit(ConstraintJointAxis.ANGULAR_Y, -YAW_LIMIT, YAW_LIMIT);
        rig.constraint().setLimit(ConstraintJointAxis.ANGULAR_Z, -ROLL_LIMIT, ROLL_LIMIT);

        for (ConstraintJointAxis axis : SWING_AXES) {
            rig.constraint().setMotor(axis, 0.0, 0.0, 4.0, false, 0.0);
        }
    }

    public static void sweepOrphans() {
        drainPendingWipes();
        purgeArrivals();
        Iterator<Entry<GhostWheelBodies.Key, GhostWheelBodies.Rig>> it = RIGS.entrySet().iterator();

        while (it.hasNext()) {
            Entry<GhostWheelBodies.Key, GhostWheelBodies.Rig> entry = it.next();
            if (!SEEN.contains(entry.getKey())) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(entry.getValue().level());
                if (container != null) {
                    boolean carLoaded = container.getSubLevel(entry.getKey().carSub()) != null;
                    if (carLoaded || SableTrainRegistry.bySubLevel(entry.getKey().carSub()) == null) {
                        destroy(entry.getValue().level(), entry.getValue());
                        DANGLING.remove(entry.getKey());
                        ALIGNING.remove(entry.getKey());
                        it.remove();
                    }
                }
            }
        }

        SEEN.clear();
    }

    private static Vector3d pivotFor(ServerSubLevel carSub, SableTrain train, Vector3d pivotInCar) {
        SableTrain.Car car = train.car();
        return car != null && car.carriage() != null && !train.poseTweak().isZero()
            ? new Vector3d(pivotInCar).add(PoseTweak.counter(carSub, pivotInCar, car.carriage().referenceForward(), train.poseTweak()))
            : pivotInCar;
    }

    public static void retarget(PhysicsPipeline pipeline, ServerSubLevel carSub, SableTrain train) {
        if (train.car() != null) {
            if (!train.isDerailed() && !train.isPhysics()) {
                UUID carId = carSub.getUniqueId();
                Quaterniond carQ = new Quaterniond(carSub.logicalPose().orientation());
                Quaterniond rollTurn = rollTurnOf(carSub, train);

                for (SableTrain.Bogey bogey : train.car().bogeys()) {
                    if (!train.isBogeyOffTrack(bogey.localPos())) {
                        GhostWheelBodies.Rig rig = RIGS.get(new GhostWheelBodies.Key(carId, bogey.localPos()));
                        if (rig != null && rig.constraint().isValid() && wheelAlive(carSub.getLevel(), rig)) {
                            Quaterniond chord = bogey.rail().chordFrameQuaternion();
                            if (chord != null) {
                                if (rollTurn != null) {
                                    chord = new Quaterniond(rollTurn).mul(chord);
                                }

                                rig.constraint().setFrame1(pivotFor(carSub, train, rig.pivotInCar()), new Quaterniond(carQ).conjugate().mul(chord).normalize());
                            }
                        }
                    }
                }
            }
        }
    }

    private static Quaterniond rollTurnOf(ServerSubLevel carSub, SableTrain train) {
        if (!CarriageRoll.isRolling(carSub.getUniqueId())) {
            return null;
        }

        double phi = CarriageRoll.of(carSub.getUniqueId()).phi;
        Vector3d[] ref = TrainRailAdvance.bogeyReference(carSub, train.car());
        return ref != null && !(Math.abs(phi) < 1.0E-6) ? new Quaterniond().rotateAxis(phi, ref[1].x, ref[1].y, ref[1].z) : null;
    }

    public static void syncSwing(SableTrain train) {
        ServerSubLevel carSub = resolveSub(train);
        SableTrain.Car car = train.car();
        if (carSub != null && car != null) {
            UUID carId = carSub.getUniqueId();
            ServerLevel level = carSub.getLevel();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            Quaterniond carQ = new Quaterniond(carSub.logicalPose().orientation());

            for (SableTrain.Bogey bogey : car.bogeys()) {
                BlockPos worldPos = carSub.getPlot().getCenterBlock().offset(bogey.localPos());
                if (level.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                    boolean loose = train.isDerailed() || train.isBogeyOffTrack(bogey.localPos());
                    GhostWheelBodies.Rig rig = loose ? RIGS.get(new GhostWheelBodies.Key(carId, bogey.localPos())) : null;
                    Quaterniond swing = rig != null && container != null ? swingOf(container, rig, carQ) : null;
                    BogeyYawVisual.setWheelSwing(be, swing == null ? null : new Quaternionf((float)swing.x, (float)swing.y, (float)swing.z, (float)swing.w));
                }
            }
        }
    }

    private static Quaterniond swingOf(ServerSubLevelContainer container, GhostWheelBodies.Rig rig, Quaterniond carQ) {
        return container.getSubLevel(rig.wheelSub()) instanceof ServerSubLevel wheel && !wheel.isRemoved()
            ? new Quaterniond(carQ)
                .conjugate()
                .mul(new Quaterniond(wheel.logicalPose().orientation()))
                .mul(new Quaterniond(rig.wheelRest()).conjugate())
                .mul(rig.carRest())
                .normalize()
            : null;
    }

    public static String plateRollLine(ServerSubLevel carSub, SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(carSub.getLevel());
        RailCarriage carriage = train.car() == null ? null : train.car().carriage();
        Vector3d forward0 = carriage == null ? null : carriage.referenceForward();
        if (container != null && forward0 != null) {
            Quaterniond carQ = new Quaterniond(carSub.logicalPose().orientation());
            StringBuilder out = new StringBuilder("[");

            for (Entry<GhostWheelBodies.Key, GhostWheelBodies.Rig> entry : RIGS.entrySet()) {
                if (entry.getKey().carSub().equals(carSub.getUniqueId())) {
                    Quaterniond swing = swingOf(container, entry.getValue(), carQ);
                    out.append(swing == null ? "gone" : LogFormat.f(Math.toDegrees(CarriageRoll.twist(new Quaterniond(), swing, forward0)))).append(' ');
                }
            }

            return out.append(']').toString();
        } else {
            return "none";
        }
    }

    public static void releaseAll(SableTrain train) {
        ServerSubLevel carSub = resolveSub(train);
        UUID carId = carSub != null ? carSub.getUniqueId() : (train.car() != null ? train.car().subLevelId() : null);
        if (carId != null) {
            ServerLevel level = carSub != null ? carSub.getLevel() : train.level();
            Iterator<Entry<GhostWheelBodies.Key, GhostWheelBodies.Rig>> it = RIGS.entrySet().iterator();

            while (it.hasNext()) {
                Entry<GhostWheelBodies.Key, GhostWheelBodies.Rig> entry = it.next();
                if (entry.getKey().carSub().equals(carId)) {
                    destroy(level, entry.getValue());
                    DANGLING.remove(entry.getKey());
                    ALIGNING.remove(entry.getKey());
                    it.remove();
                }
            }
        }
    }

    public static void rebuild(SableTrain train) {
        releaseAll(train);
        SETTLING.put(train.id(), 2);
    }

    public static Vec3 plateFacing(SableTrain train) {
        ServerSubLevel carSub = resolveSub(train);
        SableTrain.Car car = train.car();
        if (carSub != null && car != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(carSub.getLevel());
            if (container == null) {
                return null;
            }

            UUID carId = carSub.getUniqueId();

            for (SableTrain.Bogey bogey : car.bogeys()) {
                Vector3d facing = facingOf(container, RIGS.get(new GhostWheelBodies.Key(carId, bogey.localPos())));
                if (facing != null) {
                    return new Vec3(facing.x, 0.0, facing.z).normalize();
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public static Vec3 plateFacing(SableTrain train, BlockPos bogeyLocal) {
        ServerSubLevel carSub = resolveSub(train);
        if (carSub == null) {
            return null;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(carSub.getLevel());
        if (container == null) {
            return null;
        }

        Vector3d facing = facingOf(container, RIGS.get(new GhostWheelBodies.Key(carSub.getUniqueId(), bogeyLocal)));
        return facing == null ? null : new Vec3(facing.x, 0.0, facing.z).normalize();
    }

    private static Vector3d facingOf(ServerSubLevelContainer container, GhostWheelBodies.Rig rig) {
        if (rig != null && container.getSubLevel(rig.wheelSub()) instanceof ServerSubLevel wheel && !wheel.isRemoved()) {
            Vector3d facing = new Quaterniond(rig.wheelRest()).conjugate().transform(new Vector3d(rig.restFacing()));
            new Quaterniond(wheel.logicalPose().orientation()).transform(facing);
            return Math.hypot(facing.x, facing.z) > 1.0E-6 ? facing : null;
        } else {
            return null;
        }
    }

    public static boolean alignToRail(SableTrain train, BlockPos bogeyLocal, Quaterniond railChord) {
        ServerSubLevel carSub = resolveSub(train);
        if (carSub != null && railChord != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(carSub.getLevel());
            GhostWheelBodies.Key key = new GhostWheelBodies.Key(carSub.getUniqueId(), bogeyLocal);
            GhostWheelBodies.Rig rig = RIGS.get(key);
            if (container == null || rig == null || !rig.constraint().isValid() || !wheelAlive(carSub.getLevel(), rig)) {
                return true;
            }

            if (!DANGLING.contains(key)) {
                return true;
            }

            Quaterniond carQ = new Quaterniond(carSub.logicalPose().orientation());
            rig.constraint().setFrame1(pivotFor(carSub, train, rig.pivotInCar()), new Quaterniond(carQ).conjugate().mul(railChord).normalize());
            double cone = Math.max(0.0, ALIGNING.getOrDefault(key, 1.0) - 0.25);
            ALIGNING.put(key, cone);
            rig.constraint().setLimit(ConstraintJointAxis.ANGULAR_X, -PITCH_LIMIT * cone, PITCH_LIMIT * cone);
            rig.constraint().setLimit(ConstraintJointAxis.ANGULAR_Y, -YAW_LIMIT * cone, YAW_LIMIT * cone);
            rig.constraint().setLimit(ConstraintJointAxis.ANGULAR_Z, -ROLL_LIMIT * cone, ROLL_LIMIT * cone);
            Vector3d facing = facingOf(container, rig);
            if (facing == null) {
                return true;
            }

            Vector3d railForward = new Quaterniond(railChord).transform(new Vector3d(0.0, 0.0, 1.0));
            return cone <= 0.0 || SubLevelData.axisDiffDeg(facing, railForward) <= 4.0;
        } else {
            return true;
        }
    }

    public static void clearAlign(SableTrain train) {
        clearAlign(train, null);
    }

    public static void clearAlign(SableTrain train, BlockPos bogeyLocal) {
        ServerSubLevel carSub = resolveSub(train);
        UUID carId = carSub != null ? carSub.getUniqueId() : (train.car() != null ? train.car().subLevelId() : null);
        if (carId != null && !ALIGNING.isEmpty()) {
            Iterator<Entry<GhostWheelBodies.Key, Double>> it = ALIGNING.entrySet().iterator();

            while (it.hasNext()) {
                Entry<GhostWheelBodies.Key, Double> entry = it.next();
                if (entry.getKey().carSub().equals(carId) && (bogeyLocal == null || entry.getKey().bogeyLocal().equals(bogeyLocal))) {
                    GhostWheelBodies.Rig rig = RIGS.get(entry.getKey());
                    if (carSub != null && rig != null && rig.constraint().isValid() && DANGLING.contains(entry.getKey())) {
                        openDangleCone(carSub, train, rig);
                    }

                    it.remove();
                }
            }
        }
    }

    private static void attach(ServerSubLevel carSub, SableTrain train, SableTrain.Bogey bogey, GhostWheelBodies.Key key) {
        ServerLevel level = carSub.getLevel();
        BlockPos bogeyPos = carSub.getPlot().getCenterBlock().offset(bogey.localPos()).immutable();
        Quaterniond chord = bogey.rail() != null ? bogey.rail().chordFrameQuaternion() : blockFrame(carSub, level.getBlockState(bogeyPos));
        if (chord != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                List<BlockPos> pads = WheelColliders.placePads(level, bogeyPos);
                if (!pads.isEmpty()) {
                    ServerSubLevel wheelSub = SubLevelAssemblyUtil.assemble(level, bogeyPos, pads, boundsOf(pads));
                    if (wheelSub == null) {
                        LoconauticsConstants.LOGGER.warn("[ghostwheel] bogey {} failed to carve a wheel sub level", bogey.localPos().toShortString());
                        WheelColliders.clearPadsAround(level, bogeyPos);
                    } else {
                        Vector3d pivotInCar = new Vector3d(bogeyPos.getX() + 0.5, bogeyPos.getY() + 0.5, bogeyPos.getZ() + 0.5);
                        BlockPos wheelPlot = wheelSub.getPlot().getCenterBlock();
                        Vector3d pivotInWheel = new Vector3d(wheelPlot.getX() + 0.5, wheelPlot.getY() + 0.5, wheelPlot.getZ() + 0.5);
                        Quaterniond carQ = new Quaterniond(carSub.logicalPose().orientation());
                        Quaterniond frame1 = new Quaterniond(carQ).conjugate().mul(chord).normalize();
                        boolean railed = !train.isDerailed() && !train.isBogeyOffTrack(bogey.localPos());
                        Quaterniond restChord = railed ? squareToBogey(carQ, chord, level.getBlockState(bogeyPos)) : chord;
                        Quaterniond frame2 = new Quaterniond(wheelSub.logicalPose().orientation()).conjugate().mul(restChord).normalize();
                        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
                            pivotFor(carSub, train, pivotInCar), pivotInWheel, frame1, frame2, EnumSet.copyOf(LOCKED_AXES)
                        );
                        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
                        GenericConstraintHandle handle = (GenericConstraintHandle)pipeline.addConstraint(carSub, wheelSub, config);
                        if (handle == null) {
                            container.removeSubLevel(wheelSub, SubLevelRemovalReason.REMOVED);
                        } else {
                            handle.setContactsEnabled(false);

                            for (ConstraintJointAxis axis : SWING_AXES) {
                                handle.setLimit(axis, 0.0, 0.0);
                            }

                            WHEEL_BODIES.add(wheelSub.getUniqueId());
                            RIGS.put(
                                key,
                                new GhostWheelBodies.Rig(
                                    wheelSub.getUniqueId(),
                                    handle,
                                    pivotInCar,
                                    level,
                                    new Quaterniond(carQ),
                                    new Quaterniond(wheelSub.logicalPose().orientation()),
                                    restChord.transform(new Vector3d(0.0, 0.0, 1.0)),
                                    new Quaterniond(restChord)
                                )
                            );
                            DANGLING.remove(key);
                            ALIGNING.remove(key);
                        }
                    }
                }
            }
        }
    }

    private static Quaterniond blockFrame(ServerSubLevel carSub, BlockState bogeyState) {
        Quaterniond frame = new Quaterniond(carSub.logicalPose().orientation());
        if (bogeyState.hasProperty(AbstractBogeyBlock.AXIS) && bogeyState.getValue(AbstractBogeyBlock.AXIS) == Axis.X) {
            frame.rotateY(Math.PI / 2);
        }

        return frame;
    }

    private static Quaterniond squareToBogey(Quaterniond carQ, Quaterniond chord, BlockState bogeyState) {
        if (!bogeyState.hasProperty(AbstractBogeyBlock.AXIS)) {
            return chord;
        }

        Vector3d forward = new Quaterniond(carQ).conjugate().mul(chord).transform(new Vector3d(0.0, 0.0, 1.0));
        Vector3d square = bogeyState.getValue(AbstractBogeyBlock.AXIS) == Axis.X ? new Vector3d(1.0, 0.0, 0.0) : new Vector3d(0.0, 0.0, 1.0);
        if (square.x * forward.x + square.z * forward.z < 0.0) {
            square.negate();
        }

        double yaw = Math.atan2(square.z * forward.x - square.x * forward.z, square.x * forward.x + square.z * forward.z);
        return new Quaterniond(carQ).rotateY(-yaw).mul(new Quaterniond(carQ).conjugate()).mul(chord).normalize();
    }

    private static void destroy(ServerLevel level, GhostWheelBodies.Rig rig) {
        if (rig.constraint().isValid()) {
            rig.constraint().remove();
        }

        if (SubLevelSplitWatcher.inRemovalCallback()) {
            PENDING_WIPE.put(rig.wheelSub(), level);
        } else {
            WHEEL_BODIES.remove(rig.wheelSub());
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null && container.getSubLevel(rig.wheelSub()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                sub.getPlot().destroyAllBlocks();
                container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
            }
        }
    }

    private static BoundingBox3i boundsOf(List<BlockPos> pads) {
        BlockPos first = pads.get(0);
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;

        for (BlockPos pos : pads) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static ServerSubLevel resolveSub(SableTrain train) {
        SableTrain.Car car = train.car();
        UUID subId = car == null ? null : car.subLevelId();
        if (subId == null) {
            return null;
        } else {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            if (container == null) {
                return null;
            } else {
                return container.getSubLevel(subId) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
            }
        }
    }

    private record Key(UUID carSub, BlockPos bogeyLocal) {
    }

    private record Rig(
        UUID wheelSub,
        GenericConstraintHandle constraint,
        Vector3d pivotInCar,
        ServerLevel level,
        Quaterniond carRest,
        Quaterniond wheelRest,
        Vector3d restFacing,
        Quaterniond restChord
    ) {
    }
}
