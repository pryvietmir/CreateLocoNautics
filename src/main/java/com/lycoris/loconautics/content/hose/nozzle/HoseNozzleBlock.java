package com.lycoris.loconautics.content.hose.nozzle;

import com.lycoris.loconautics.common.SubLevelAssemblyUtil;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HoseNozzleBlock extends Block implements IWrenchable, IBE<HoseNozzleBlockEntity>, BlockSubLevelCollisionShape, BlockSubLevelAssemblyListener {
    private static final VoxelShape SHAPE = Shapes.or(
        Shapes.box(0.28125, 0.0, 0.28125, 0.71875, 0.375, 0.71875), Shapes.box(0.34375, 0.375, 0.34375, 0.65625, 0.5, 0.65625)
    );
    private static final VoxelShape PHYSICS_SHAPE = Shapes.box(0.28125, 0.0, 0.28125, 0.71875, 0.3125, 0.71875);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public HoseNozzleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)this.defaultBlockState().setValue(POWERED, false));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{POWERED});
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return PHYSICS_SHAPE;
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            SubLevelAssemblyUtil.assembleSingleBlock(serverLevel, pos, "[hose]");
        }
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    public Class<HoseNozzleBlockEntity> getBlockEntityClass() {
        return HoseNozzleBlockEntity.class;
    }

    public BlockEntityType<? extends HoseNozzleBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends HoseNozzleBlockEntity>)LoconauticsRegistries.HOSE_NOZZLE_BE.get();
    }
}
