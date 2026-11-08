package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class PanelPartVisual {
    private final VisualizationContext context;
    private final List<TransformedInstance> instances = new ArrayList<>();

    protected PanelPartVisual(VisualizationContext context) {
        this.context = context;
    }

    public abstract boolean matches(PanelPart var1);

    public abstract void update(PanelBlockEntity var1, PanelSlot var2, PoseStack var3, float var4, int var5, boolean var6);

    protected TransformedInstance create(Model model) {
        TransformedInstance instance = (TransformedInstance)this.context.instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance();
        this.instances.add(instance);
        return instance;
    }

    protected static void place(TransformedInstance instance, PoseStack ms, int light) {
        instance.setTransform(ms);
        instance.light(light);
        instance.setChanged();
    }

    public void collectCrumbling(Consumer<Instance> consumer) {
        this.instances.forEach(consumer);
    }

    public void delete() {
        this.instances.forEach(AbstractInstance::delete);
        this.instances.clear();
    }
}
