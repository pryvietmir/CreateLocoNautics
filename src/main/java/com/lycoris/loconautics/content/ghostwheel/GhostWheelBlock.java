package com.lycoris.loconautics.content.ghostwheel;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

public class GhostWheelBlock extends Block implements BlockWithSubLevelCollisionCallback, BlockSubLevelCollisionShape {
    public static final MapCodec<GhostWheelBlock> CODEC = simpleCodec(GhostWheelBlock::new);
    public static final IntegerProperty CORNER = IntegerProperty.create("corner", 0, 3);
    public static final BooleanProperty MIDDLE = BooleanProperty.create("middle");
    public static final BooleanProperty UPSIDE_DOWN = BooleanProperty.create("upside_down");
    public static final BooleanProperty ON_MONORAIL = BooleanProperty.create("on_monorail");
    public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty ALONG = IntegerProperty.create("along", 0, 2);
    private static final double END_REACH = 0.75;
    private static final double COLLISION_FLOOR = 0.3125;

    public GhostWheelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
            (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(CORNER, 0))
                                .setValue(MIDDLE, false))
                            .setValue(UPSIDE_DOWN, false))
                        .setValue(ON_MONORAIL, false))
                    .setValue(AXIS, Axis.Z))
                .setValue(ALONG, 0)
        );
    }

    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return GhostWheelCollisionCallback.INSTANCE;
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{CORNER, MIDDLE, UPSIDE_DOWN, ON_MONORAIL, AXIS, ALONG});
    }

    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return padShape(state);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return padShape(state);
    }

    private static VoxelShape padShape(BlockState state) {
        boolean monorail = (Boolean)state.getValue(ON_MONORAIL);
        double floor = monorail ? 0.96875 : 0.3125;
        if (floor < 0.0 || floor >= 1.0) {
            LoconauticsConstants.LOGGER.warn("GhostWheel COLLISION_FLOOR {} is out of [0,1). Collision lives within ONE cell. Clamping.", floor);
            floor = Math.max(0.0, Math.min(0.9999, floor));
        }

        boolean hanging = (Boolean)state.getValue(UPSIDE_DOWN);
        double yLo = hanging ? (monorail ? floor : 0.0) : floor;
        double yHi = hanging && !monorail ? 1.0 - floor : 1.0;
        double acrossLo = 0.0;
        double acrossHi = 1.0;
        if (!(Boolean)state.getValue(MIDDLE)) {
            boolean towardMinus = (Integer)state.getValue(CORNER) == 1;
            acrossLo = towardMinus ? 0.0 : 0.5;
            acrossHi = towardMinus ? 0.5 : 1.0;
        }

        int end = (Integer)state.getValue(ALONG);
        double alongLo = end == 2 ? 0.25 : 0.0;
        double alongHi = end == 1 ? 0.75 : 1.0;
        return state.getValue(AXIS) == Axis.X
            ? Shapes.box(alongLo, yLo, acrossLo, alongHi, yHi, acrossHi)
            : Shapes.box(acrossLo, yLo, alongLo, acrossHi, yHi, alongHi);
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, BlockState entityState, LivingEntity entity, int numberOfParticles) {
        return true;
    }

    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        return true;
    }

    public static IClientBlockExtensions clientExtensions() {
        return new IClientBlockExtensions() {
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
                return true;
            }

            public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
                return true;
            }
        };
    }
}
