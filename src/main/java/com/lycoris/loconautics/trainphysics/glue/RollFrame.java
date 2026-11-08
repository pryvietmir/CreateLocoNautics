package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SeatBlend;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RollFrame {
    public final Vector3d reference;
    public final Vector3d tangent;
    public final Quaterniond seat;
    public final Vector3d up;
    public final Vector3d lateral;
    public final Vector3d forward0;
    public final Vector3d up0;
    public final Vector3d lateral0;
    public final Vector3d anchorLocal;
    public final Vector3d gLocal;
    public final double half;

    private RollFrame(Vector3d reference, Vector3d tangent, Quaterniond seat, Vector3d forward0, Vector3d up0, Vector3d anchorLocal, double half) {
        this.reference = reference;
        this.tangent = tangent;
        this.seat = seat;
        this.forward0 = forward0;
        this.up0 = up0;
        this.up = seat.transform(new Vector3d(up0));
        this.lateral = new Vector3d(tangent).cross(this.up).normalize();
        this.lateral0 = new Vector3d(forward0).cross(up0).normalize();
        this.anchorLocal = anchorLocal;
        this.gLocal = new Vector3d(anchorLocal).fma(-0.7163999999999999, up0);
        this.half = half;
    }

    public static RollFrame of(ServerSubLevel sub, SableTrain.Car car, Vector3d[] ref) {
        RailCarriage carriage = car.carriage();
        Vector3d anchor = RailSnap.glueAnchorLocal(sub, car);
        if (carriage != null && anchor != null && ref != null) {
            Quaterniond seat = carriage.orientationTo(ref[1]);
            Vector3d forward0 = carriage.referenceForward();
            Vector3d up0 = carriage.referenceUp();
            return forward0 != null && up0 != null
                ? new RollFrame(
                    new Vector3d(ref[0]),
                    new Vector3d(ref[1]),
                    seat,
                    new Vector3d(forward0),
                    new Vector3d(up0),
                    anchor,
                    RailGauge.halfGauge(carriage.trackMaterial())
                )
                : null;
        } else {
            return null;
        }
    }

    public Vector3d pointLocal(int side) {
        return new Vector3d(this.gLocal).fma(side * this.half, this.lateral0);
    }

    public PoseTweak.Applied seated(ServerSubLevel sub, SableTrain train, boolean decayBlend) {
        CarriageRoll.Roll roll = CarriageRoll.rollOf(sub);
        return this.leaned(sub, this.seatedLevel(sub, train, decayBlend), roll == null ? 0.0 : roll.suspension);
    }

    public PoseTweak.Applied seatedLevel(ServerSubLevel sub, SableTrain train, boolean decayBlend) {
        Quaterniond blended = SeatBlend.q(sub.getUniqueId(), this.seat, decayBlend);
        PoseTweak.Applied tweaked = PoseTweak.apply(
            sub, RailSnap.anchorTo(sub, this.anchorLocal, this.reference, blended), blended, this.forward0, train.poseTweak()
        );
        return new PoseTweak.Applied(SeatBlend.pos(sub.getUniqueId(), tweaked.position()), tweaked.orientation());
    }

    public PoseTweak.Applied leaned(ServerSubLevel sub, PoseTweak.Applied level, double lean) {
        if (Math.abs(lean) < 1.0E-7) {
            return level;
        }

        Vector3d pivot = seatedPoint(sub, level, this.gLocal);
        Quaterniond turned = new Quaterniond(level.orientation()).rotateAxis(lean, this.forward0.x, this.forward0.y, this.forward0.z);
        Vector3d position = pivot.sub(turned.transform(new Vector3d(this.gLocal).sub(sub.logicalPose().rotationPoint())));
        return new PoseTweak.Applied(position, turned);
    }

    public static Vector3d seatedPoint(ServerSubLevel sub, PoseTweak.Applied seated, Vector3d local) {
        Vector3d offset = new Vector3d(local).sub(sub.logicalPose().rotationPoint());
        return seated.orientation().transform(offset).add(seated.position());
    }
}
