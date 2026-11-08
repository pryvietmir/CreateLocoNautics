package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HoseConnectorBlock extends Block implements IBE<HoseConnectorBlockEntity>, IWrenchable, BlockSubLevelAssemblyListener, BlockSubLevelCollisionShape {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShaper SHAPES = VoxelShaper.forDirectional(
        Shapes.or(
            box(3.0, 3.0, 0.0, 13.0, 13.0, 2.0),
            new VoxelShape[]{box(4.0, 4.0, 1.0, 12.0, 12.0, 5.0), box(2.95, 2.95, 5.0, 13.05, 13.05, 8.1), box(4.95, 4.95, 7.95, 11.05, 11.05, 10.95)}
        ),
        Direction.NORTH
    );
    private static final VoxelShaper PHYSICS_SHAPES = VoxelShaper.forDirectional(box(3.0, 3.0, 0.0, 13.0, 13.0, 0.25), Direction.NORTH);

    public HoseConnectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState)state.setValue(FACING, rotation.rotate((Direction)state.getValue(FACING)));
    }

    protected BlockState mirror(BlockState state, Mirror mirror) {
        return (BlockState)state.setValue(FACING, mirror.mirror((Direction)state.getValue(FACING)));
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(shapeSide((Direction)state.getValue(FACING)));
    }

    private static Direction shapeSide(Direction facing) {
        return facing.getAxis() == Axis.Y ? facing.getOpposite() : facing;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return PHYSICS_SHAPES.get(shapeSide((Direction)state.getValue(FACING)));
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            BlockPos var8 = context.getClickedPos();
            ServerPlayer player = context.getPlayer() instanceof ServerPlayer sp ? sp : null;
            if (level.getBlockEntity(var8) != null && HoseLinks.detachNewest(level, level.getBlockEntity(var8), player)) {
                return InteractionResult.SUCCESS;
            }

            level.destroyBlock(var8, player == null || !player.isCreative());
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    public Class<HoseConnectorBlockEntity> getBlockEntityClass() {
        return HoseConnectorBlockEntity.class;
    }

    public BlockEntityType<? extends HoseConnectorBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends HoseConnectorBlockEntity>)LoconauticsRegistries.HOSE_CONNECTOR_BE.get();
    }
}
