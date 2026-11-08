package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public final class EnginePistons {
    private static final float SHAKE_SLOW = 0.025F;
    private static final float SHAKE_FAST = 0.0075F;
    private static final float TOP_RPM = 256.0F;
    private static final float FADE_RPM = 8.0F;
    private static final float WIDE_SHARE = 0.8125F;
    private static final float MID_SHARE = 0.375F;
    private static final float TIGHT_SHARE = 0.125F;
    private static final float MID_SIZE = 0.45F;
    private static final float TIGHT_SIZE = 0.1F;

    private EnginePistons() {
    }

    public static PartialModel model(BlockState state) {
        return LoconauticsPartialModels.ENGINE_PISTONS.get(((SmallEngineBlock)state.getBlock()).getColor());
    }

    public static float yaw(BlockState state) {
        return 180.0F - ((Direction)state.getValue(SmallEngineBlock.HORIZONTAL_FACING)).toYRot();
    }

    public static Vector3f shake(float angle, float seed, float rpm, float share) {
        float tighten = Mth.clamp((share - 0.125F) / 0.6875F, 0.0F, 1.0F);
        float scale = share >= 0.375F
            ? Mth.lerp(Mth.clamp((share - 0.375F) / 0.4375F, 0.0F, 1.0F), 0.45F, 1.0F)
            : Mth.lerp(Mth.clamp((share - 0.125F) / 0.25F, 0.0F, 1.0F), 0.1F, 0.45F);
        float size = Mth.lerp(Mth.clamp(rpm / 256.0F, 0.0F, 1.0F), 0.025F, 0.0075F) * Mth.clamp(rpm / 8.0F, 0.0F, 1.0F) * scale;
        float roundness = 1.2F * tighten;
        float thump = Mth.sin(angle * 2.0F + seed);
        return new Vector3f(thump * size * 0.4F, Mth.sin(angle * 2.0F + seed + roundness) * size, 0.0F);
    }
}
