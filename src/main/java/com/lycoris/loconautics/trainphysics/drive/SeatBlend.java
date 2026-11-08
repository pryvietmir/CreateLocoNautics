package com.lycoris.loconautics.trainphysics.drive;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class SeatBlend {
    static final Map<UUID, Vector3d> SEAT_BLEND_POS = new HashMap<>();
    static final Map<UUID, Quaterniond> SEAT_BLEND_Q = new HashMap<>();
    static final double SEAT_BLEND_RATE = 0.12;
    static final double SEAT_BLEND_DONE = 0.02;
    static final double SEAT_BLEND_MAX = 2.0;

    private SeatBlend() {
    }

    public static void begin(ServerSubLevel sub, Vector3d seatPos, Quaterniond seatQ) {
        UUID id = sub.getUniqueId();
        Vector3d off = new Vector3d(sub.logicalPose().position()).sub(seatPos);
        if (off.length() > 2.0) {
            end(id);
        } else {
            SEAT_BLEND_POS.put(id, off);
            SEAT_BLEND_Q.put(id, new Quaterniond(seatQ).invert().mul(new Quaterniond(sub.logicalPose().orientation())).normalize());
        }
    }

    public static void end(UUID id) {
        SEAT_BLEND_POS.remove(id);
        SEAT_BLEND_Q.remove(id);
    }

    public static Quaterniond q(UUID id, Quaterniond q, boolean decay) {
        Quaterniond qErr = SEAT_BLEND_Q.get(id);
        if (qErr == null) {
            return q;
        }

        if (decay) {
            SEAT_BLEND_POS.get(id).mul(0.88);
            qErr.slerp(new Quaterniond(), 0.12).normalize();
        }

        return new Quaterniond(q).mul(qErr).normalize();
    }

    public static Vector3d pos(UUID id, Vector3d target) {
        Vector3d off = SEAT_BLEND_POS.get(id);
        if (off == null) {
            return target;
        } else if (off.length() <= 0.02) {
            end(id);
            return target;
        } else {
            return new Vector3d(target).add(off);
        }
    }

    public static boolean active(UUID id) {
        return SEAT_BLEND_Q.containsKey(id);
    }

    public static void retain(Set<UUID> live) {
        SEAT_BLEND_POS.keySet().retainAll(live);
        SEAT_BLEND_Q.keySet().retainAll(live);
    }
}
