package com.lycoris.loconautics.client.copycat;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

public class CopycatBedLoader implements IGeometryLoader<CopycatBedLoader.Geometry> {
    public CopycatBedLoader.Geometry read(JsonObject json, JsonDeserializationContext context) {
        return new CopycatBedLoader.Geometry(pixels(json, "low", 2.0F), pixels(json, "high", 4.0F), pixels(json, "inset", 2.0F));
    }

    private static float pixels(JsonObject json, String key, float fallback) {
        return (json.has(key) ? json.get(key).getAsFloat() : fallback) / 16.0F;
    }

    public record Geometry(float low, float high, float inset) implements IUnbakedGeometry<CopycatBedLoader.Geometry> {
        public BakedModel bake(
            IGeometryBakingContext context,
            ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState,
            ItemOverrides overrides
        ) {
            TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
            return new CopycatBedModel(this.low, this.high, this.inset, particle, context.getTransforms());
        }
    }
}
