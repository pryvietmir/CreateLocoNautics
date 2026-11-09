package com.lycoris.loconautics.content.controls.panel.visual;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.model.Model.ConfiguredMesh;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

public final class ShiftedModels {
    private static final float SHEET_PIXELS = 64.0F;
    private static final RendererReloadCache<ShiftedModels.Key, Model> CACHE = new RendererReloadCache<>(ShiftedModels::build);

    private ShiftedModels() {
    }

    public static Model plain(PartialModel partial) {
        return Models.partial(partial);
    }

    public static Model shifted(PartialModel partial, SpriteShiftEntry entry, int pixelsU, int pixelsV) {
        return (Model)CACHE.get(new ShiftedModels.Key(partial, entry, pixelsU, pixelsV, null));
    }

    public static Model withTransparency(PartialModel partial, Transparency transparency) {
        return (Model)CACHE.get(new ShiftedModels.Key(partial, null, 0, 0, transparency));
    }

    private static Model build(ShiftedModels.Key key) {
        float du = 0.0F;
        float dv = 0.0F;
        if (key.entry() != null) {
            TextureAtlasSprite original = key.entry().getOriginal();
            TextureAtlasSprite target = key.entry().getTarget();
            du = target.getU0() - original.getU0() + (target.getU1() - target.getU0()) / 64.0F * key.pixelsU();
            dv = target.getV0() - original.getV0() + (target.getV1() - target.getV0()) / 64.0F * key.pixelsV();
        }

        List<ConfiguredMesh> meshes = new ArrayList<>();

        for (ConfiguredMesh configured : Models.partial(key.partial()).meshes()) {
            Material material = configured.material();
            if (key.transparency() != null) {
                material = SimpleMaterial.builderOf(material).transparency(key.transparency()).build();
            }

            Mesh mesh = du == 0.0F && dv == 0.0F ? configured.mesh() : new UvShiftedMesh(configured.mesh(), du, dv);
            meshes.add(new ConfiguredMesh(material, mesh));
        }

        return new SimpleModel(meshes);
    }

    private record Key(PartialModel partial, @Nullable SpriteShiftEntry entry, int pixelsU, int pixelsV, @Nullable Transparency transparency) {
    }
}
