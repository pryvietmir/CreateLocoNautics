package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelRenderer;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.lycoris.loconautics.content.dashboard.DashboardBlock;
import com.lycoris.loconautics.content.dashboard.DashboardRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual.Context;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PanelVisual extends AbstractBlockEntityVisual<PanelBlockEntity> implements SimpleDynamicVisual {
    private final VisualizationContext context;
    private final Map<PanelSlot, PanelPartVisual> parts = new EnumMap<>(PanelSlot.class);
    private final PoseStack pose = new PoseStack();
    private BlockState lastState;
    private boolean lastCentered;
    private volatile int light;
    private volatile boolean lightChanged = true;

    public PanelVisual(VisualizationContext context, PanelBlockEntity panel, float partialTick) {
        super(context, panel, partialTick);
        this.context = context;
        this.light = this.computePackedLight();
        this.refresh(partialTick);
    }

    public void beginFrame(Context ctx) {
        if (((PanelBlockEntity)this.blockEntity).isRemoved() || this.level.getBlockEntity(this.pos) != this.blockEntity) {
            this.delete();
        } else if (!this.doDistanceLimitThisFrame(ctx) && this.isVisible(ctx.frustum())) {
            this.refresh(ctx.partialTick());
        }
    }

    public void updateLight(float partialTick) {
        this.light = this.computePackedLight();
        this.lightChanged = true;
    }

    private void refresh(float partialTick) {
        BlockState state = ((PanelBlockEntity)this.blockEntity).getBlockState();
        boolean centered = ((PanelBlockEntity)this.blockEntity).isCentered();
        boolean full = this.lightChanged || state != this.lastState || centered != this.lastCentered;
        this.lastState = state;
        this.lastCentered = centered;
        this.lightChanged = false;

        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart part = ((PanelBlockEntity)this.blockEntity).getPart(slot);
            PanelPartVisual visual = this.parts.get(slot);
            boolean slotFull = full;
            if (part == null) {
                if (visual != null) {
                    visual.delete();
                    this.parts.remove(slot);
                }
            } else {
                if (visual == null || !visual.matches(part)) {
                    if (visual != null) {
                        visual.delete();
                    }

                    visual = this.create(part);
                    if (visual == null) {
                        this.parts.remove(slot);
                        continue;
                    }

                    this.parts.put(slot, visual);
                    slotFull = true;
                }

                this.pose.pushPose();
                this.pose.translate(this.visualPos.getX(), this.visualPos.getY(), this.visualPos.getZ());
                if (state.getBlock() instanceof DashboardBlock) {
                    DashboardRenderer.placeBoard(this.pose, state);
                } else {
                    PanelRenderer.placePanel(this.pose, state);
                }

                double offsetX = centered ? 4.0 : slot.modelX;
                double offsetZ = centered ? 4.0 : slot.modelZ;
                this.pose.translate(offsetX / 16.0, 0.0, offsetZ / 16.0);
                visual.update((PanelBlockEntity)this.blockEntity, slot, this.pose, partialTick, this.light, slotFull);
                this.pose.popPose();
            }
        }
    }

    @Nullable
    private PanelPartVisual create(PanelPart part) {
        return switch (part) {
            case GaugeBlockEntity.Gauge gauge -> new GaugePartVisual(this.context, gauge);
            case LeverPart lever -> new LeverPartVisual(this.context, lever);
            case SwitchPart flip -> new SwitchPartVisual(this.context, flip);
            case ButtonPart button -> new ButtonPartVisual(this.context, button);
            case ValvePart valve -> new ValvePartVisual(this.context, valve);
            default -> null;
        };
    }

    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        this.parts.values().forEach(visual -> visual.collectCrumbling(consumer));
    }

    protected void _delete() {
        this.parts.values().forEach(PanelPartVisual::delete);
        this.parts.clear();
    }
}
