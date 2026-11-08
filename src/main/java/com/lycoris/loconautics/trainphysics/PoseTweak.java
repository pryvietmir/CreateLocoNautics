package com.lycoris.loconautics.trainphysics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class PoseTweak {
    private static final double DEFAULT_LERP_SPEED = 0.2;
    private static final double SNAP_DISTANCE = 1.0E-4;
    public static final double MAX_ANGLE = 360.0;
    public static final double MAX_OFFSET = 32.0;
    private double roll;
    private double pitch;
    private double yaw;
    private double x;
    private double y;
    private double z;
    private double targetRoll;
    private double targetPitch;
    private double targetYaw;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double lerpSpeed = 0.2;

    public double roll() {
        return this.roll;
    }

    public double pitch() {
        return this.pitch;
    }

    public double yaw() {
        return this.yaw;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public double targetRoll() {
        return this.targetRoll;
    }

    public double targetPitch() {
        return this.targetPitch;
    }

    public double targetYaw() {
        return this.targetYaw;
    }

    public double targetX() {
        return this.targetX;
    }

    public double targetY() {
        return this.targetY;
    }

    public double targetZ() {
        return this.targetZ;
    }

    public double lerpSpeed() {
        return this.lerpSpeed;
    }

    public void setLerpSpeed(double lerpSpeed) {
        this.lerpSpeed = Double.isNaN(lerpSpeed) ? 0.2 : Math.max(0.0, Math.min(1.0, lerpSpeed));
    }

    public void setRoll(double roll) {
        this.targetRoll = clampAngle(roll);
    }

    public void setPitch(double pitch) {
        this.targetPitch = clampAngle(pitch);
    }

    public void setYaw(double yaw) {
        this.targetYaw = clampAngle(yaw);
    }

    public void setOffset(double x, double y, double z) {
        this.targetX = clampOffset(x);
        this.targetY = clampOffset(y);
        this.targetZ = clampOffset(z);
    }

    public static double clampAngle(double degrees) {
        return Double.isNaN(degrees) ? 0.0 : Math.max(-360.0, Math.min(360.0, degrees));
    }

    public static double clampOffset(double blocks) {
        return Double.isNaN(blocks) ? 0.0 : Math.max(-32.0, Math.min(32.0, blocks));
    }

    public void reset() {
        this.setRoll(0.0);
        this.setPitch(0.0);
        this.setYaw(0.0);
        this.setOffset(0.0, 0.0, 0.0);
    }

    public void advance() {
        this.roll = this.step(this.roll, this.targetRoll);
        this.pitch = this.step(this.pitch, this.targetPitch);
        this.yaw = this.step(this.yaw, this.targetYaw);
        this.x = this.step(this.x, this.targetX);
        this.y = this.step(this.y, this.targetY);
        this.z = this.step(this.z, this.targetZ);
    }

    private double step(double current, double target) {
        double moved = current + (target - current) * this.lerpSpeed;
        return Math.abs(target - moved) < 1.0E-4 ? target : moved;
    }

    public boolean isSettled() {
        return this.roll == this.targetRoll
            && this.pitch == this.targetPitch
            && this.yaw == this.targetYaw
            && this.x == this.targetX
            && this.y == this.targetY
            && this.z == this.targetZ;
    }

    public void snap() {
        this.roll = this.targetRoll;
        this.pitch = this.targetPitch;
        this.yaw = this.targetYaw;
        this.x = this.targetX;
        this.y = this.targetY;
        this.z = this.targetZ;
    }

    public boolean isZero() {
        return this.roll == 0.0 && this.pitch == 0.0 && this.yaw == 0.0 && this.x == 0.0 && this.y == 0.0 && this.z == 0.0;
    }

    public Quaterniond local(Vector3d referenceForward) {
        if (this.roll == 0.0 && this.pitch == 0.0 && this.yaw == 0.0) {
            return new Quaterniond();
        }

        Vector3d forward = referenceForward != null && !(referenceForward.lengthSquared() < 1.0E-9)
            ? new Vector3d(referenceForward).normalize()
            : new Vector3d(1.0, 0.0, 0.0);
        Vector3d lateral = new Vector3d(0.0, 1.0, 0.0).cross(forward);
        if (lateral.lengthSquared() < 1.0E-9) {
            lateral.set(0.0, 0.0, 1.0);
        }

        lateral.normalize();
        return new Quaterniond()
            .rotateAxis(Math.toRadians(this.yaw), 0.0, 1.0, 0.0)
            .rotateAxis(Math.toRadians(this.pitch), lateral.x, lateral.y, lateral.z)
            .rotateAxis(Math.toRadians(this.roll), forward.x, forward.y, forward.z)
            .normalize();
    }

    public Vector3d offset() {
        return new Vector3d(this.x, this.y, this.z);
    }

    public static Vector3d anchorCenter(ServerSubLevel sub) {
        BlockPos a = sub.getPlot().getCenterBlock();
        return new Vector3d(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
    }

    public static PoseTweak.Applied apply(ServerSubLevel sub, Vector3d pos, Quaterniond q, Vector3d referenceForward, PoseTweak tweak) {
        if (tweak != null && !tweak.isZero()) {
            Quaterniond turned = new Quaterniond(q).mul(tweak.local(referenceForward)).normalize();
            Vector3dc rotationPoint = sub.logicalPose().rotationPoint();
            Vector3d anchorToPoint = new Vector3d(rotationPoint).sub(anchorCenter(sub));
            Vector3d anchorWorld = q.transform(new Vector3d(anchorToPoint).negate()).add(pos);
            Vector3d moved = turned.transform(new Vector3d(anchorToPoint).add(tweak.offset())).add(anchorWorld);
            return new PoseTweak.Applied(moved, turned);
        } else {
            return new PoseTweak.Applied(new Vector3d(pos), new Quaterniond(q));
        }
    }

    public static Vector3d counter(ServerSubLevel sub, Vector3d subLevelPos, Vector3d referenceForward, PoseTweak tweak) {
        if (tweak != null && !tweak.isZero()) {
            Vector3d arm = new Vector3d(subLevelPos).sub(anchorCenter(sub));
            return tweak.local(referenceForward).transformInverse(new Vector3d(arm)).sub(arm).sub(tweak.offset());
        } else {
            return new Vector3d();
        }
    }

    public void save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putDouble("Roll", this.targetRoll);
        t.putDouble("Pitch", this.targetPitch);
        t.putDouble("Yaw", this.targetYaw);
        t.putDouble("X", this.targetX);
        t.putDouble("Y", this.targetY);
        t.putDouble("Z", this.targetZ);
        t.putDouble("LerpSpeed", this.lerpSpeed);
        tag.put("PoseTweak", t);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("PoseTweak")) {
            this.reset();
            this.snap();
        } else {
            CompoundTag t = tag.getCompound("PoseTweak");
            this.targetRoll = t.getDouble("Roll");
            this.targetPitch = t.getDouble("Pitch");
            this.targetYaw = t.getDouble("Yaw");
            this.targetX = t.getDouble("X");
            this.targetY = t.getDouble("Y");
            this.targetZ = t.getDouble("Z");
            this.setLerpSpeed(t.contains("LerpSpeed") ? t.getDouble("LerpSpeed") : 0.2);
            this.snap();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "roll %.2f, pitch %.2f, yaw %.2f, offset (%.3f, %.3f, %.3f), lerp %.2f",
            this.targetRoll,
            this.targetPitch,
            this.targetYaw,
            this.targetX,
            this.targetY,
            this.targetZ,
            this.lerpSpeed
        );
    }

    public record Applied(Vector3d position, Quaterniond orientation) {
    }
}
