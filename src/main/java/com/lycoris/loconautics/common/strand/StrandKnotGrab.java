package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.LoconauticsConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class StrandKnotGrab {
    private static final double GRAVITY = 9.8;
    private static final double DAMPING = 0.92;
    private static final double CONTACT_GRIP = 0.7;
    public static final int TRACE_TICKS = 100;
    private static final double REACH_SLACK = 4.0;
    private static final double TAUT = 0.999;
    private static final int ITERATIONS = 12;
    private static final int AFTER_COLLISION_ITERATIONS = 3;
    private volatile StrandKnotGrab.Request request;
    private volatile boolean engaged;
    private int index;
    private double[] rest = new double[0];
    private final List<Vector3d> previous = new ArrayList<>();
    public int traceTicks;

    public boolean idle() {
        return this.request == null && !this.engaged;
    }

    public boolean engaged() {
        return this.engaged;
    }

    public void request(UUID player, int knotIndex, float range) {
        StrandKnotGrab.Request current = this.request;
        if (current == null || current.player().equals(player)) {
            this.request = new StrandKnotGrab.Request(player, knotIndex, Mth.clamp(range, 1.0F, 5.0F));
        }
    }

    public void stop(UUID player) {
        StrandKnotGrab.Request current = this.request;
        if (current != null && current.player().equals(player)) {
            this.request = null;
        }
    }

    public void reset() {
        this.request = null;
        this.engaged = false;
    }

    public boolean tickAndReportRelease(
        ServerLevel level, List<Vector3d> points, @Nullable Vector3d pinStart, @Nullable Vector3d pinEnd, double dt, double radius
    ) {
        StrandKnotGrab.Request req = this.request;
        Player player = req == null ? null : level.getPlayerByUUID(req.player());
        if (req == null || player == null || !player.isAlive() || player.isSpectator()) {
            return this.finish(req == null ? "stop requested" : "player gone");
        }

        if (!this.engaged && !this.begin(points, req.index())) {
            this.request = null;
            return false;
        }

        if (this.rest.length != points.size() - 1) {
            return this.finish("point count changed");
        }

        Vector3d knot = points.get(this.index);
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 4.0;
        if (player.getEyePosition().distanceToSqr(knot.x, knot.y, knot.z) > reach * reach) {
            return this.finish("out of reach");
        }

        this.step(level, points, this.goal(player, req.range(), pinStart, pinEnd), pinStart, pinEnd, dt, radius);
        return false;
    }

    private boolean finish(String reason) {
        boolean was = this.engaged;
        if (was) {
            this.traceTicks = 100;
            LoconauticsConstants.LOGGER.debug("[knotgrab] released reason={} points={}", reason, this.previous.size());
        }

        this.request = null;
        this.engaged = false;
        this.previous.clear();
        return was;
    }

    private boolean begin(List<Vector3d> points, int knotIndex) {
        if (knotIndex >= 1 && knotIndex <= points.size() - 2) {
            this.index = knotIndex;
            this.rest = new double[points.size() - 1];
            this.previous.clear();

            for (int i = 0; i < points.size(); i++) {
                this.previous.add(new Vector3d((Vector3dc)points.get(i)));
                if (i > 0) {
                    this.rest[i - 1] = points.get(i).distance((Vector3dc)points.get(i - 1));
                }
            }

            this.engaged = true;
            LoconauticsConstants.LOGGER.debug("[knotgrab] engaged index={} points={}", knotIndex, points.size());
            return true;
        } else {
            return false;
        }
    }

    private Vector3d goal(Player player, float range, @Nullable Vector3d pinStart, @Nullable Vector3d pinEnd) {
        Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(range));
        Vector3d goal = new Vector3d(target.x, target.y, target.z);
        double before = 0.0;
        double after = 0.0;

        for (int i = 0; i < this.rest.length; i++) {
            if (i < this.index) {
                before += this.rest[i];
            } else {
                after += this.rest[i];
            }
        }

        for (int pass = 0; pass < 2; pass++) {
            limit(goal, pinStart, before);
            limit(goal, pinEnd, after);
        }

        return goal;
    }

    private static void limit(Vector3d goal, @Nullable Vector3d pin, double slack) {
        if (pin != null) {
            double max = slack * 0.999;
            double dist = goal.distance(pin);
            if (dist > max && dist > 1.0E-9) {
                goal.sub(pin).mul(max / dist).add(pin);
            }
        }
    }

    private boolean fixed(int i, int last, @Nullable Vector3d pinStart, @Nullable Vector3d pinEnd) {
        return i == this.index || i == 0 && pinStart != null || i == last && pinEnd != null;
    }

    private void step(ServerLevel level, List<Vector3d> points, Vector3d goal, @Nullable Vector3d pinStart, @Nullable Vector3d pinEnd, double dt, double radius) {
        int last = points.size() - 1;
        double fall = 9.8 * dt * dt;

        for (int i = 0; i <= last; i++) {
            Vector3d cur = points.get(i);
            Vector3d prev = this.previous.get(i);
            if (this.fixed(i, last, pinStart, pinEnd)) {
                cur.set(i == this.index ? goal : (i == 0 ? pinStart : pinEnd));
                prev.set(cur);
            } else {
                double vx = (cur.x - prev.x) * 0.92;
                double vy = (cur.y - prev.y) * 0.92;
                double vz = (cur.z - prev.z) * 0.92;
                prev.set(cur);
                cur.add(vx, vy - fall, vz);
            }
        }

        this.relax(points, 12, pinStart, pinEnd);
        this.collide(level, points, radius);
        this.relax(points, 3, pinStart, pinEnd);
        this.collide(level, points, radius);
    }

    private void collide(ServerLevel level, List<Vector3d> points, double radius) {
        for (int i = 1; i < points.size() - 1; i++) {
            if (StrandCollision.pushOut(level, points.get(i), radius)) {
                this.previous.get(i).lerp((Vector3dc)points.get(i), 0.7);
            }
        }
    }

    private void relax(List<Vector3d> points, int iterations, @Nullable Vector3d pinStart, @Nullable Vector3d pinEnd) {
        int last = points.size() - 1;
        Vector3d delta = new Vector3d();

        for (int it = 0; it < iterations; it++) {
            for (int i = 0; i < last; i++) {
                boolean fixedA = this.fixed(i, last, pinStart, pinEnd);
                boolean fixedB = this.fixed(i + 1, last, pinStart, pinEnd);
                if (!fixedA || !fixedB) {
                    Vector3d a = points.get(i);
                    Vector3d b = points.get(i + 1);
                    b.sub(a, delta);
                    double len = delta.length();
                    if (!(len < 1.0E-9)) {
                        double push = (len - this.rest[i]) / len;
                        if (fixedA) {
                            b.fma(-push, delta);
                        } else if (fixedB) {
                            a.fma(push, delta);
                        } else {
                            a.fma(push * 0.5, delta);
                            b.fma(-push * 0.5, delta);
                        }
                    }
                }
            }
        }
    }

    private record Request(UUID player, int index, float range) {
    }
}
