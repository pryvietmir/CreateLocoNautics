package com.lycoris.loconautics.mixin.client.track;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual.Context;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrackVisual.class, remap = false)
public abstract class TrackVisualCullingMixin implements SimpleDynamicVisual {
    @Shadow
    @Final
    private List<?> visuals;
    @Shadow
    @Final
    protected TrackBlockEntity blockEntity;
    @Shadow
    @Final
    protected BlockPos pos;
    @Shadow
    @Final
    protected BlockPos visualPos;
    @Unique
    private boolean loconautics$culled;
    @Unique
    private AABB loconautics$bounds;
    @Unique
    private boolean loconautics$boundsDirty = true;

    @Shadow
    public abstract void _delete();

    @Shadow
    private void collectConnections() {
        throw new AssertionError();
    }

    @Inject(method = "collectConnections", at = @At("TAIL"), remap = false)
    private void loconautics$markBoundsDirty(CallbackInfo ci) {
        this.loconautics$boundsDirty = true;
    }

    @Inject(method = "update", at = @At("HEAD"), remap = false)
    private void loconautics$markBoundsDirtyOnUpdate(float pt, CallbackInfo ci) {
        this.loconautics$boundsDirty = true;
    }

    public void beginFrame(Context ctx) {
        if (this.loconautics$boundsDirty) {
            this.loconautics$boundsDirty = false;
            this.loconautics$bounds = null;

            for (BezierConnection bc : this.blockEntity.getConnections().values()) {
                if (bc.isPrimary()) {
                    AABB b = bc.getBounds();
                    this.loconautics$bounds = this.loconautics$bounds == null ? b : this.loconautics$bounds.minmax(b);
                }
            }
        }

        AABB bounds = this.loconautics$bounds;
        if (bounds != null) {
            int roX = this.pos.getX() - this.visualPos.getX();
            int roY = this.pos.getY() - this.visualPos.getY();
            int roZ = this.pos.getZ() - this.visualPos.getZ();
            boolean visible = ctx.frustum()
                .testAab(
                    (float)(bounds.minX - roX),
                    (float)(bounds.minY - roY),
                    (float)(bounds.minZ - roZ),
                    (float)(bounds.maxX - roX),
                    (float)(bounds.maxY - roY),
                    (float)(bounds.maxZ - roZ)
                );
            if (visible) {
                if (this.loconautics$culled || this.visuals.isEmpty()) {
                    this.collectConnections();
                    this.loconautics$culled = false;
                }
            } else if (!this.loconautics$culled || !this.visuals.isEmpty()) {
                this._delete();
                this.loconautics$culled = true;
            }
        }
    }
}
