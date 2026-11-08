package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.BodyRef;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle.AttachmentPoint;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class RopePullTrace {
    private static final int WINDOW_TICKS = 20;
    private static final double LOG_ABOVE = 0.02;
    private static final double ENDS_ABOVE = 0.05;
    private static final long LOG_GAP_TICKS = 10L;
    private static final Map<UUID, RopePullTrace.Window> WINDOWS = new HashMap<>();
    private static final Map<UUID, RopePullTrace.Snap> SNAPS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ENDS_LOGGED = new HashMap<>();
    private static final Map<UUID, Long> SOLVE_LOGGED = new HashMap<>();

    private RopePullTrace() {
    }

    public static void noteSnap(ServerSubLevel body, double distance) {
        long tick = body.getLevel().getGameTime();
        SNAPS.merge(
            body.getUniqueId(),
            new RopePullTrace.Snap(tick, distance),
            (old, now) -> old.tick() == now.tick() ? new RopePullTrace.Snap(tick, Math.max(old.worst(), now.worst())) : now
        );
    }

    public static void ends(AttachedRopePhysicsObject rope, ServerLevel level) {
        List<Vector3d> points = rope.getPoints();
        if (points.size() >= 2) {
            double startOff = endOffset(rope, level, AttachmentPoint.START, points.get(0));
            double endOff = endOffset(rope, level, AttachmentPoint.END, points.get(points.size() - 1));
            long now = level.getGameTime();
            if (!(Math.max(Double.isNaN(startOff) ? 0.0 : startOff, Double.isNaN(endOff) ? 0.0 : endOff) < 0.05)
                && now - ENDS_LOGGED.getOrDefault(rope.getUUID(), -4611686018427387904L) >= 10L) {
                ENDS_LOGGED.put(rope.getUUID(), now);
                StrandEnd startEnd = rope.endOf(AttachmentPoint.START);
                StrandEnd endEnd = rope.endOf(AttachmentPoint.END);
                LoconauticsConstants.LOGGER
                    .info(
                        String.format(
                            Locale.ROOT,
                            "[ropeforce] ends %s rope=%s startOff=%.3f endOff=%.3f len=%.3f start=[%s] end=[%s]",
                            rope.getClass().getSimpleName(),
                            LogFormat.shortId(rope.getUUID()),
                            startOff,
                            endOff,
                            rope.getCurrentExtension(),
                            bodyInfo(level, startEnd == null ? null : startEnd.subLevelId(), now),
                            bodyInfo(level, endEnd == null ? null : endEnd.subLevelId(), now)
                        )
                    );
            }
        }
    }

    private static double endOffset(AttachedRopePhysicsObject rope, ServerLevel level, AttachmentPoint point, Vector3d actual) {
        AttachedRopePhysicsObject.Attachment att = rope.attachments.get(point);
        if (att == null) {
            return Double.NaN;
        }

        BodyRef body = BodyRef.of(level, att.subLevelId());
        if (body.absent()) {
            return Double.NaN;
        }

        Vector3d anchor = rope.holderPoint(level, att, body.sub());
        if (body.sub() != null) {
            anchor = body.sub().logicalPose().transformPosition(anchor, new Vector3d());
        }

        return anchor.distance(actual);
    }

    private static String bodyInfo(ServerLevel level, @Nullable UUID id, long now) {
        ServerSubLevel sub = BodyRef.of(level, id).sub();
        if (sub == null) {
            return "world";
        }

        RopePullTrace.Snap snap = SNAPS.get(id);
        double snapped = snap != null && now - snap.tick() <= 1L ? snap.worst() : 0.0;
        return String.format(
            Locale.ROOT, "%s snap=%.3f staff=%s speed=%.2f", LogFormat.shortId(id), snapped, ExternalForceDerail.staffHolds(sub), velocityOf(sub).length()
        );
    }

    private static void solve(AttachedRopePhysicsObject rope, ServerSubLevel a, ServerSubLevel b, long now) {
        if ((ExternalForceDerail.staffHolds(a) || ExternalForceDerail.staffHolds(b)) && SOLVE_LOGGED.getOrDefault(rope.getUUID(), Long.MIN_VALUE) != now) {
            SOLVE_LOGGED.put(rope.getUUID(), now);
            Vector3d linear = new Vector3d();
            rope.jointImpulses(linear, new Vector3d());
            LoconauticsConstants.LOGGER
                .info(
                    String.format(
                        Locale.ROOT,
                        "[ropeforce] solve %s rope=%s a=%s staff=%s vx=%.2f b=%s staff=%s vx=%.2f len=%.3f joint=%s jointImpulse=%.3f",
                        rope.getClass().getSimpleName(),
                        LogFormat.shortId(rope.getUUID()),
                        LogFormat.shortId(a.getUniqueId()),
                        ExternalForceDerail.staffHolds(a),
                        velocityOf(a).x,
                        LogFormat.shortId(b.getUniqueId()),
                        ExternalForceDerail.staffHolds(b),
                        velocityOf(b).x,
                        rope.getCurrentExtension(),
                        rope.constraintLive(),
                        linear.length()
                    )
                );
        }
    }

    static void sample(AttachedRopePhysicsObject rope, ServerLevel level) {
        StrandEnd startEnd = rope.endOf(AttachmentPoint.START);
        StrandEnd endEnd = rope.endOf(AttachmentPoint.END);
        if (startEnd != null && endEnd != null) {
            ServerSubLevel a = BodyRef.of(level, startEnd.subLevelId()).sub();
            ServerSubLevel b = BodyRef.of(level, endEnd.subLevelId()).sub();
            if (a != null && b != null) {
                boolean same = a == b;
                if (same || TrainConsist.connectedSubLevels(level, a.getUniqueId()).contains(b.getUniqueId())) {
                    long now = level.getGameTime();
                    solve(rope, a, b, now);
                    RopePullTrace.Window w = WINDOWS.computeIfAbsent(rope.getUUID(), id -> new RopePullTrace.Window());
                    Vector3d com = centerVelocity(a, b, same);
                    w.maxExtension = Math.max(w.maxExtension, rope.getCurrentExtension());
                    if (w.startTick == Long.MIN_VALUE) {
                        w.startTick = now;
                        w.startCom.set(com);
                    } else if (now - w.startTick >= 20L) {
                        double dvHorizontal = Math.hypot(com.x - w.startCom.x, com.z - w.startCom.z);
                        if (dvHorizontal > 0.02) {
                            LoconauticsConstants.LOGGER
                                .info(
                                    String.format(
                                        Locale.ROOT,
                                        "[ropepull] %s rope=%s relation=%s bodies=%s/%s massA=%.1f massB=%.1f comDvHorizontal=%.4f comVel=(%.3f,%.3f,%.3f) len=%.3f maxLen=%.3f joint=%s pts=%d",
                                        rope.getClass().getSimpleName(),
                                        LogFormat.shortId(rope.getUUID()),
                                        same ? "SAME_BODY" : "COUPLED",
                                        LogFormat.shortId(a.getUniqueId()),
                                        LogFormat.shortId(b.getUniqueId()),
                                        a.getMassTracker().getMass(),
                                        b.getMassTracker().getMass(),
                                        dvHorizontal,
                                        com.x,
                                        com.y,
                                        com.z,
                                        rope.getCurrentExtension(),
                                        w.maxExtension,
                                        rope.constraintLive(),
                                        rope.getPoints().size()
                                    )
                                );
                        }

                        w.startTick = now;
                        w.startCom.set(com);
                        w.maxExtension = 0.0;
                    }
                }
            }
        }
    }

    private static Vector3d centerVelocity(ServerSubLevel a, ServerSubLevel b, boolean same) {
        Vector3d va = velocityOf(a);
        if (same) {
            return va;
        }

        double ma = a.getMassTracker().getMass();
        double mb = b.getMassTracker().getMass();
        double total = Math.max(1.0E-6, ma + mb);
        return va.mul(ma / total).add(velocityOf(b).mul(mb / total));
    }

    private static Vector3d velocityOf(@Nullable ServerSubLevel body) {
        Vector3d velocity = new Vector3d();
        RigidBodyHandle handle = body == null ? null : RigidBodyHandle.of(body);
        if (handle != null && handle.isValid()) {
            handle.getLinearVelocity(velocity);
        }

        return velocity;
    }

    private record Snap(long tick, double worst) {
    }

    private static final class Window {
        long startTick = Long.MIN_VALUE;
        final Vector3d startCom = new Vector3d();
        double maxExtension;
    }
}
