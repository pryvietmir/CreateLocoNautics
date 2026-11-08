package com.lycoris.loconautics.mixin.create.track;

import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrackEdge.class, remap = false)
public abstract class TrackEdgeSeamMixin {
    @Shadow
    public TrackNode node1;
    @Shadow
    public TrackNode node2;
    @Unique
    private Boolean loconautics$seam;

    @Inject(method = "isInterDimensional", at = @At("HEAD"), cancellable = true)
    private void loconautics$seamIsInterDimensional(CallbackInfoReturnable<Boolean> cir) {
        if (this.loconautics$seam == null) {
            if (this.node1 == null || this.node2 == null) {
                return;
            }

            this.loconautics$seam = RailSeams.seamState(this.node1.getLocation(), this.node2.getLocation());
            if (this.loconautics$seam == null) {
                return;
            }
        }

        if (this.loconautics$seam) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canTravelTo", at = @At("HEAD"), cancellable = true)
    private void loconautics$createTrainsStopAtSeam(TrackEdge other, CallbackInfoReturnable<Boolean> cir) {
        if (RailSeams.createTrainTravelling()) {
            TrackEdge self = (TrackEdge)this;
            if (RailSeams.isSeam(self.node1.getLocation(), self.node2.getLocation()) || RailSeams.isSeam(other.node1.getLocation(), other.node2.getLocation())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "getLength", at = @At("HEAD"), cancellable = true)
    private void loconautics$seamLength(CallbackInfoReturnable<Double> cir) {
        if (this.loconautics$isSeam()) {
            cir.setReturnValue(RailSeams.seamLength(this.node1.getLocation(), this.node2.getLocation()));
        }
    }

    @Inject(method = "getPosition", at = @At("HEAD"), cancellable = true)
    private void loconautics$seamPosition(TrackGraph graph, double t, CallbackInfoReturnable<Vec3> cir) {
        if (this.loconautics$isSeam()) {
            cir.setReturnValue(RailSeams.seamPoint(this.node1.getLocation(), this.node2.getLocation(), t));
        }
    }

    @Unique
    private boolean loconautics$isSeam() {
        return ((TrackEdge)this).isInterDimensional() && Boolean.TRUE.equals(this.loconautics$seam);
    }
}
