package com.lycoris.loconautics.trainphysics.portal;

import java.util.UUID;

public record PortalCut(UUID subLevelId, int axis, int cutoff, boolean positive) {
    public static PortalCut everything(UUID subLevelId) {
        return new PortalCut(subLevelId, 0, Integer.MIN_VALUE, true);
    }

    public boolean hidden(int x, int y, int z) {
        int coord = this.axis == 0 ? x : (this.axis == 1 ? y : z);
        return this.positive ? coord > this.cutoff : coord < this.cutoff;
    }

    public boolean noCollider(int x, int y, int z) {
        return this.hidden(x, y, z) || this.seam(x, y, z);
    }

    public boolean seam(int x, int y, int z) {
        return (this.axis == 0 ? x : (this.axis == 1 ? y : z)) == this.cutoff;
    }
}
