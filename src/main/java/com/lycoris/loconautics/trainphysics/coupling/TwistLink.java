package com.lycoris.loconautics.trainphysics.coupling;

import java.util.UUID;
import org.joml.Vector3d;

public record TwistLink(UUID subA, Vector3d anchorA, UUID subB, Vector3d anchorB, TwistLink.Kind kind, double strength, Vector3d axisA) {
    public UUID other(UUID id) {
        return id.equals(this.subA) ? this.subB : this.subA;
    }

    public enum Kind {
        KNUCKLE,
        SPRING,
        SWIVEL;
    }
}
