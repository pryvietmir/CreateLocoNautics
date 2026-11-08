package com.lycoris.loconautics.trainphysics.portal;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.util.Map.Entry;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public record PortalFrame(
    ResourceKey<Level> nearDim,
    ResourceKey<Level> farDim,
    TrackNodeLocation nearLoc,
    TrackNodeLocation farLoc,
    Vec3 nearPoint,
    Vec3 farPoint,
    Vec3 inward,
    double yaw
) {
    private static final double ZERO_LENGTH = 1.0E-6;

    public static PortalFrame of(TrackGraph graph, Couple<TrackNodeLocation> crossing, ResourceKey<Level> home) {
        if (graph != null
            && crossing != null
            && home != null
            && crossing.getFirst() != null
            && crossing.getSecond() != null
            && ((TrackNodeLocation)crossing.getFirst()).dimension != null
            && ((TrackNodeLocation)crossing.getSecond()).dimension != null) {
            boolean firstHome = ((TrackNodeLocation)crossing.getFirst()).dimension.equals(home);
            TrackNodeLocation near = (TrackNodeLocation)crossing.get(firstHome);
            TrackNodeLocation far = (TrackNodeLocation)crossing.get(!firstHome);
            if (near.dimension.equals(home) && !far.dimension.equals(home)) {
                Vec3 away = awayFrom(graph, near);
                Vec3 out = awayFrom(graph, far);
                if (away != null && out != null) {
                    Vec3 inward = away.reverse();
                    double yaw = heading(out) - heading(inward);
                    return new PortalFrame(near.dimension, far.dimension, near, far, near.getLocation(), far.getLocation(), inward, yaw);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static Vec3 awayFrom(TrackGraph graph, TrackNodeLocation loc) {
        TrackNode node = graph.locateNode(loc);
        if (node == null) {
            return null;
        }

        for (Entry<TrackNode, TrackEdge> entry : graph.getConnectionsFrom(node).entrySet()) {
            if (!(entry.getValue().getLength() <= 1.0E-6)) {
                Vec3 d = entry.getValue().getDirection(true);
                Vec3 flat = new Vec3(d.x, 0.0, d.z);
                if (flat.lengthSqr() > 1.0E-6) {
                    return flat.normalize();
                }
            }
        }

        return null;
    }

    private static double heading(Vec3 d) {
        return Math.atan2(d.x, d.z);
    }

    public PortalFrame inverted() {
        return new PortalFrame(
            this.farDim, this.nearDim, this.farLoc, this.nearLoc, this.farPoint, this.nearPoint, this.dirToFar(this.inward).reverse(), -this.yaw
        );
    }

    public boolean isPortal(Couple<TrackNodeLocation> crossing) {
        if (crossing == null) {
            return false;
        }

        TrackNodeLocation a = (TrackNodeLocation)crossing.getFirst();
        TrackNodeLocation b = (TrackNodeLocation)crossing.getSecond();
        return this.nearLoc.equals(a) && this.farLoc.equals(b) || this.nearLoc.equals(b) && this.farLoc.equals(a);
    }

    public boolean samePortal(PortalFrame other) {
        return other != null
            && (
                this.nearLoc.equals(other.nearLoc) && this.farLoc.equals(other.farLoc)
                    || this.nearLoc.equals(other.farLoc) && this.farLoc.equals(other.nearLoc)
            );
    }

    public Quaterniond rotation() {
        return new Quaterniond().rotateY(this.yaw);
    }

    public Vec3 toFar(Vec3 near) {
        Vector3d v = this.toFar(new Vector3d(near.x, near.y, near.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 toNear(Vec3 far) {
        Vector3d v = this.toNear(new Vector3d(far.x, far.y, far.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vector3d toFar(Vector3dc near) {
        Vector3d offset = new Vector3d(near).sub(this.nearPoint.x, this.nearPoint.y, this.nearPoint.z);
        return this.rotation().transform(offset).add(this.farPoint.x, this.farPoint.y, this.farPoint.z);
    }

    public Vector3d toNear(Vector3dc far) {
        Vector3d offset = new Vector3d(far).sub(this.farPoint.x, this.farPoint.y, this.farPoint.z);
        return this.rotation().conjugate().transform(offset).add(this.nearPoint.x, this.nearPoint.y, this.nearPoint.z);
    }

    public Vec3 dirToFar(Vec3 near) {
        Vector3d v = this.rotation().transform(new Vector3d(near.x, near.y, near.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 dirToNear(Vec3 far) {
        Vector3d v = this.rotation().conjugate().transform(new Vector3d(far.x, far.y, far.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vector3d dirToFar(Vector3dc near) {
        return this.rotation().transform(new Vector3d(near));
    }

    public Vector3d dirToNear(Vector3dc far) {
        return this.rotation().conjugate().transform(new Vector3d(far));
    }

    public Quaterniond orientToFar(Quaterniondc near) {
        return this.rotation().mul(near).normalize();
    }

    public Quaterniond orientToNear(Quaterniondc far) {
        return this.rotation().conjugate().mul(far).normalize();
    }

    public double depth(Vec3 nearPos) {
        return nearPos.subtract(this.nearPoint).dot(this.inward);
    }

    public double depth(Vector3dc nearPos) {
        return (nearPos.x() - this.nearPoint.x) * this.inward.x
            + (nearPos.y() - this.nearPoint.y) * this.inward.y
            + (nearPos.z() - this.nearPoint.z) * this.inward.z;
    }

    public Vec3 farInward() {
        return this.dirToFar(this.inward).reverse();
    }
}
