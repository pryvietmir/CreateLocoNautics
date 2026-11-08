package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

public final class KnuckleConnectionManager {
    private KnuckleConnectionManager() {
    }

    public static void watch(ServerSubLevelContainer container) {
        container.addObserver(new KnuckleConnectionManager.Watcher(container.getLevel()));
    }

    private static void connect(ServerSubLevel subA, KnuckleBlockEntity beA, ServerSubLevel subB, KnuckleBlockEntity beB) {
        if (KnuckleRailCoupler.glued(subA) && KnuckleRailCoupler.glued(subB)) {
            beA.setConnection(subB.getUniqueId(), beB.getBlockPos());
            beB.setConnection(subA.getUniqueId(), beA.getBlockPos());
            SubLevelContacts.disableCoupled(subA, subB);
        }
    }

    private static boolean tornApart(KnuckleBlockEntity be, ServerSubLevel sub, ServerSubLevel partnerSub, double gap) {
        return gap > 8.0
            ? true
            : be.engageDone()
                && (!KnuckleRailCoupler.rigid(sub) || !KnuckleRailCoupler.rigid(partnerSub))
                && !ExternalForceDerail.staffHolds(sub)
                && !ExternalForceDerail.staffHolds(partnerSub);
    }

    private static boolean canMate(KnuckleConnectionManager.Free a, KnuckleConnectionManager.Free b, double reach) {
        if (a.sub() != b.sub() && a.ready() && b.ready() && !(a.facing().dot(b.facing()) > -0.4)) {
            Vector3d toB = b.tip().sub(a.tip(), new Vector3d());
            return toB.length() >= reach ? false : toB.dot(a.facing()) >= -0.25 && -toB.dot(b.facing()) >= -0.25;
        } else {
            return false;
        }
    }

    private static boolean canConnect(KnuckleConnectionManager.Free a, KnuckleConnectionManager.Free b) {
        if (!canMate(a, b, KnuckleTuning.connectDistance)) {
            return false;
        }

        Vector3d toB = b.tip().sub(a.tip(), new Vector3d());
        return toB.fma(-toB.dot(a.facing()), a.facing(), new Vector3d()).length() <= 0.3;
    }

    private static boolean canResume(ServerSubLevel sub, KnuckleBlockEntity be, KnuckleBlockEntity partnerBe) {
        return partnerBe.isReleasing()
            && sub.getUniqueId().equals(partnerBe.getPartnerSubLevelId())
            && be.getBlockPos().equals(partnerBe.getPartnerLocalPos())
            && !be.isRedstoneLocked()
            && !partnerBe.isRedstoneLocked()
            && !be.isCouplingLocked()
            && !partnerBe.isCouplingLocked();
    }

    private static double hingeGap(ServerSubLevel subA, KnuckleBlockEntity beA, ServerSubLevel subB, KnuckleBlockEntity beB) {
        Vector3d hingeA = subA.logicalPose().transformPosition(KnuckleBlockEntity.hingeLocal(beA.getBlockPos(), facing(beA)));
        Vector3d hingeB = subB.logicalPose().transformPosition(KnuckleBlockEntity.hingeLocal(beB.getBlockPos(), facing(beB)));
        return hingeA.distance(hingeB);
    }

    public static void disconnect(Level level, BlockPos worldPos) {
        if (level instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(worldPos) instanceof KnuckleBlockEntity be && be.isConnected()) {
                KnuckleBlockEntity partnerBe = loadedPartner(serverLevel, be);
                if (partnerBe != null && partnerBe.isConnected()) {
                    partnerBe.clearConnection();
                }

                be.clearConnection();
            }
        }
    }

    public static void sever(Level level, BlockPos worldPos) {
        if (level instanceof ServerLevel serverLevel && serverLevel.getBlockEntity(worldPos) instanceof KnuckleBlockEntity be) {
            KnuckleBlockEntity partnerBe = loadedPartner(serverLevel, be);
            if (partnerBe != null && worldPos.equals(partnerBe.getPartnerLocalPos())) {
                partnerBe.clearConnection();
                partnerBe.clearPartner();
            }
        }
    }

    private static KnuckleBlockEntity loadedPartner(ServerLevel level, KnuckleBlockEntity be) {
        UUID partnerSubLevelId = be.getPartnerSubLevelId();
        BlockPos partnerLocalPos = be.getPartnerLocalPos();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null
            && partnerSubLevelId != null
            && partnerLocalPos != null
            && container.getSubLevel(partnerSubLevelId) instanceof ServerSubLevel partnerSub) {
            return partnerSub.getLevel().getBlockEntity(partnerLocalPos) instanceof KnuckleBlockEntity partnerBe ? partnerBe : null;
        } else {
            return null;
        }
    }

    private static Map<UUID, List<KnuckleBlockEntity>> collectKnuckles(ServerSubLevelContainer container) {
        Map<UUID, List<KnuckleBlockEntity>> cache = new HashMap<>();

        for (ServerSubLevel sub : container.getAllSubLevels()) {
            if (!sub.isRemoved()) {
                List<KnuckleBlockEntity> list = SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class);
                if (!list.isEmpty()) {
                    cache.put(sub.getUniqueId(), list);
                }
            }
        }

        return cache;
    }

    private static ServerSubLevel asSubLevel(ServerSubLevelContainer container, UUID id) {
        return id != null && container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
    }

    private static Direction facing(KnuckleBlockEntity be) {
        return (Direction)be.getBlockState().getValue(KnuckleBlock.FACING);
    }

    private static Vector3d worldFacing(ServerSubLevel sub, Direction facing) {
        Vector3d dir = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return sub.logicalPose().transformNormal(dir, dir).normalize();
    }

    private record Free(KnuckleBlockEntity be, ServerSubLevel sub, Vector3d tip, Vector3d facing, boolean ready) {
    }

    private record Watch(KnuckleBlockEntity beA, ServerSubLevel subA, KnuckleBlockEntity beB, ServerSubLevel subB) {
    }

    private static final class Watcher implements SubLevelObserver {
        private final ServerLevel level;
        private final Set<UUID> removed = ConcurrentHashMap.newKeySet();
        private List<KnuckleConnectionManager.Watch> watched = new ArrayList<>();
        private int ticks;

        private Watcher(ServerLevel level) {
            this.level = level;
        }

        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            if (reason == SubLevelRemovalReason.REMOVED) {
                KnuckleJoint.releaseAllFor(subLevel.getUniqueId());
                KnuckleRailCoupler.releaseAllFor(subLevel.getUniqueId());
                if (!SableTeleportation.isInFlight(subLevel.getUniqueId())) {
                    this.removed.add(subLevel.getUniqueId());
                }
            }
        }

        @Override
        public void tick(SubLevelContainer subLevels) {
            if (subLevels instanceof ServerSubLevelContainer container) {
                if (this.ticks++ % (Integer)LoconauticsConfig.KNUCKLE_SCAN_INTERVAL.get() != 0) {
                    this.coupleWatched();
                } else {
                    Map<UUID, List<KnuckleBlockEntity>> knuckles = KnuckleConnectionManager.collectKnuckles(container);
                    this.dropRemovedPartners(container, knuckles);
                    if (!knuckles.isEmpty()) {
                        this.scanForNewConnections(container, knuckles);
                        this.validateExistingConnections(container, knuckles);
                    }
                }
            }
        }

        private void dropRemovedPartners(ServerSubLevelContainer container, Map<UUID, List<KnuckleBlockEntity>> knuckles) {
            if (!this.removed.isEmpty()) {
                for (List<KnuckleBlockEntity> list : knuckles.values()) {
                    for (KnuckleBlockEntity be : list) {
                        UUID partner = be.getPartnerSubLevelId();
                        boolean alive = partner != null && container.getSubLevel(partner) instanceof ServerSubLevel live && !live.isRemoved();
                        if (partner != null && this.removed.contains(partner) && !alive) {
                            LoconauticsConstants.LOGGER.debug("[iss234k] uncouple {} reason=partner was removed", be.getBlockPos());
                            be.clearConnection();
                            be.clearPartner();
                        }
                    }
                }

                this.removed.clear();
            }
        }

        private void coupleWatched() {
            for (KnuckleConnectionManager.Watch w : this.watched) {
                if (!w.subA().isRemoved()
                    && !w.subB().isRemoved()
                    && !w.beA().isRemoved()
                    && !w.beB().isRemoved()
                    && this.available(w.beA())
                    && this.available(w.beB())
                    && KnuckleConnectionManager.canConnect(this.freeOf(w.beA(), w.subA()), this.freeOf(w.beB(), w.subB()))) {
                    KnuckleConnectionManager.connect(w.subA(), w.beA(), w.subB(), w.beB());
                }
            }
        }

        private void scanForNewConnections(ServerSubLevelContainer container, Map<UUID, List<KnuckleBlockEntity>> knuckles) {
            List<KnuckleConnectionManager.Free> free = new ArrayList<>();

            for (Entry<UUID, List<KnuckleBlockEntity>> entry : knuckles.entrySet()) {
                ServerSubLevel sub = KnuckleConnectionManager.asSubLevel(container, entry.getKey());
                if (sub != null) {
                    for (KnuckleBlockEntity be : entry.getValue()) {
                        if (this.available(be)) {
                            free.add(this.freeOf(be, sub));
                        }
                    }
                }
            }

            List<KnuckleConnectionManager.Watch> near = new ArrayList<>();

            for (int i = 0; i < free.size(); i++) {
                KnuckleConnectionManager.Free a = free.get(i);

                for (int j = i + 1; j < free.size() && !a.be().isConnected(); j++) {
                    KnuckleConnectionManager.Free b = free.get(j);
                    if (!b.be().isConnected()) {
                        if (KnuckleConnectionManager.canConnect(a, b)) {
                            KnuckleConnectionManager.connect(a.sub(), a.be(), b.sub(), b.be());
                        } else if (KnuckleConnectionManager.canMate(a, b, 6.0)) {
                            near.add(new KnuckleConnectionManager.Watch(a.be(), a.sub(), b.be(), b.sub()));
                        }
                    }
                }
            }

            this.watched = near;
        }

        private boolean available(KnuckleBlockEntity be) {
            return !be.isConnected() && !be.isReleasing() && !be.isCouplingLocked() && !be.isRedstoneLocked() && !this.stillHeldApart(be);
        }

        private boolean stillHeldApart(KnuckleBlockEntity be) {
            if (!be.isHeldApart()) {
                return false;
            }

            ServerSubLevelContainer container = SubLevelContainer.getContainer(this.level);
            ServerSubLevel own = Sable.HELPER.getContaining(be) instanceof ServerSubLevel s ? s : null;
            ServerSubLevel other = container == null ? null : KnuckleConnectionManager.asSubLevel(container, be.holdSubLevelId());
            if (own != null
                && other != null
                && be.holdLocalPos() != null
                && other.getLevel().getBlockEntity(be.holdLocalPos()) instanceof KnuckleBlockEntity otherBe
                && KnuckleConnectionManager.hingeGap(own, be, other, otherBe) < KnuckleTuning.mateOpenGap) {
                return true;
            }

            be.releaseHold();
            LoconauticsConstants.LOGGER.debug("[maneuver] decouple lock released {}", be.getBlockPos());
            return false;
        }

        private KnuckleConnectionManager.Free freeOf(KnuckleBlockEntity be, ServerSubLevel sub) {
            Vector3d facing = KnuckleConnectionManager.worldFacing(sub, KnuckleConnectionManager.facing(be));
            return new KnuckleConnectionManager.Free(
                be,
                sub,
                sub.logicalPose().transformPosition(KnuckleBlockEntity.tipLocal(be.getBlockPos(), KnuckleConnectionManager.facing(be))),
                facing,
                matable(sub, facing)
            );
        }

        private static boolean matable(ServerSubLevel sub, Vector3d facing) {
            SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
            if (train != null && !train.isDerailed() && KnuckleRailCoupler.glued(sub)) {
                for (SableTrain.Bogey bogey : train.car().bogeys()) {
                    if (train.isBogeyOffTrack(bogey.localPos())) {
                        return false;
                    }
                }

                Vector3d up = sub.logicalPose().transformNormal(new Vector3d(0.0, 1.0, 0.0), new Vector3d()).normalize();
                return Math.abs(facing.y) <= 0.05 && Math.sqrt(up.x * up.x + up.z * up.z) <= 0.05;
            } else {
                return false;
            }
        }

        private void validateExistingConnections(ServerSubLevelContainer container, Map<UUID, List<KnuckleBlockEntity>> knuckles) {
            for (Entry<UUID, List<KnuckleBlockEntity>> entry : knuckles.entrySet()) {
                ServerSubLevel sub = KnuckleConnectionManager.asSubLevel(container, entry.getKey());
                if (sub != null) {
                    for (KnuckleBlockEntity be : entry.getValue()) {
                        if (be.isConnected()) {
                            this.validateOne(container, sub, be);
                        } else if (be.isReleasing()) {
                            this.tickRelease(container, sub, be);
                        }
                    }
                }
            }
        }

        private void tickRelease(ServerSubLevelContainer container, ServerSubLevel sub, KnuckleBlockEntity be) {
            ServerSubLevel partnerSub = KnuckleConnectionManager.asSubLevel(container, be.getPartnerSubLevelId());
            BlockPos partnerPos = be.getPartnerLocalPos();
            if (!(partnerSub != null && partnerPos != null && partnerSub.getLevel().getBlockEntity(partnerPos) instanceof KnuckleBlockEntity partnerBe)) {
                be.clearPartner();
            } else if (KnuckleConnectionManager.canResume(sub, be, partnerBe)) {
                be.setConnection(partnerSub.getUniqueId(), partnerPos);
                partnerBe.setConnection(sub.getUniqueId(), be.getBlockPos());
                SubLevelContacts.disableCoupled(sub, partnerSub);
            } else {
                if (KnuckleConnectionManager.hingeGap(sub, be, partnerSub, partnerBe) >= KnuckleTuning.mateOpenGap) {
                    be.clearPartner();
                }
            }
        }

        private void validateOne(ServerSubLevelContainer container, ServerSubLevel sub, KnuckleBlockEntity be) {
            UUID partnerId = be.getPartnerSubLevelId();
            BlockPos partnerPos = be.getPartnerLocalPos();
            if (partnerId != null && partnerPos != null) {
                SubLevel loaded = container.getSubLevel(partnerId);
                if (loaded != null) {
                    if (!(loaded instanceof ServerSubLevel partnerSub && !partnerSub.isRemoved())) {
                        LoconauticsConstants.LOGGER.debug("[iss234k] uncouple {} reason=partner removed or not a server sub level", be.getBlockPos());
                        be.clearConnection();
                        be.clearPartner();
                    } else if (partnerSub.getLevel().getBlockEntity(partnerPos) instanceof KnuckleBlockEntity partnerBe
                        && partnerBe.isConnected()
                        && sub.getUniqueId().equals(partnerBe.getPartnerSubLevelId())
                        && be.getBlockPos().equals(partnerBe.getPartnerLocalPos())) {
                        be.partnerSeen();
                        SubLevelContacts.disableCoupled(sub, partnerSub);
                        double gap = KnuckleConnectionManager.hingeGap(sub, be, partnerSub, partnerBe);
                        if (be.isRedstoneLocked() || partnerBe.isRedstoneLocked() || KnuckleConnectionManager.tornApart(be, sub, partnerSub, gap)) {
                            LoconauticsConstants.LOGGER
                                .debug(
                                    "[iss234k] uncouple {} reason=torn or locked gap={} redstone={}/{} derailed={}/{}",
                                    new Object[]{
                                        be.getBlockPos(),
                                        gap,
                                        be.isRedstoneLocked(),
                                        partnerBe.isRedstoneLocked(),
                                        KnuckleJoint.derailed(sub),
                                        KnuckleJoint.derailed(partnerSub)
                                    }
                                );
                            KnuckleConnectionManager.disconnect(this.level, be.getBlockPos());
                        }
                    } else {
                        if (be.partnerMissedTooLong()) {
                            LoconauticsConstants.LOGGER
                                .debug(
                                    "[iss234k] uncouple {} reason=partner block entity missing partnerPos={} partnerBe={} partnerBack={} mePosOnPartner={} level={}",
                                    new Object[]{
                                        be.getBlockPos(),
                                        partnerPos,
                                        partnerSub.getLevel().getBlockEntity(partnerPos),
                                        partnerSub.getLevel().getBlockEntity(partnerPos) instanceof KnuckleBlockEntity back
                                            ? back.getPartnerLocalPos() + "/" + back.getPartnerSubLevelId()
                                            : "n/a",
                                        be.getBlockPos(),
                                        partnerSub.getLevel().dimension().location()
                                    }
                                );
                            be.clearConnection();
                            be.clearPartner();
                        }
                    }
                }
            } else {
                LoconauticsConstants.LOGGER.debug("[iss234k] uncouple {} reason=no partner recorded", be.getBlockPos());
                be.clearConnection();
            }
        }
    }
}
