package com.lycoris.loconautics.trainphysics.debug.force;

public enum ForceSource {
    TRACTION("traction", 3407667),
    BRAKE("brake", 16724787),
    ROLLING("rolling", 16744448),
    SLOPE_RELIEF("slope relief", 10526720),
    READBACK_CAP("readback cap", 16776960),
    SPEED_CAP("speed cap", 16764928),
    RAIL_SNAP("rail snap", 65535),
    RAIL_PIN("rail pin", 41215),
    DERAILED_DRIVE("derailed drive", 8454016),
    KNOCKBACK("knockback", 16711935),
    DERAIL_LAUNCH("derail launch", 16736448),
    RAIL_RECOIL("rail recoil", 12607743),
    COLLISION("collision", 16711808),
    RAIL_REWIND("collision rewind", 16748640),
    HOST_REACTION("host reaction", 9474192),
    EXPLOSION("explosion", 16728064),
    EXTERNAL("external push", 65408),
    HINGE("wheel hinge", 4259776),
    CURVE_RELIEF("curve relief", 12648256),
    KNUCKLE("knuckle", 16760896),
    HOSE("hose", 4235519),
    SOLVER("solver", 16777215);

    private final String label;
    private final int color;

    ForceSource(String label, int color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return this.label;
    }

    public int color() {
        return this.color;
    }
}
