package com.lycoris.loconautics.content.ball;

import com.lycoris.loconautics.common.SubLevelAssemblyUtil;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BallBlock extends Block implements IBE<BallBlockEntity>, BlockSubLevelCollisionShape {
    private static final VoxelShape SHAPE = Shapes.block();

    public BallBlock(Properties properties) {
        super(properties);
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return SHAPE;
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            SubLevelAssemblyUtil.assembleSingleBlock(serverLevel, pos, "[ball]");
        }
    }

    public Class<BallBlockEntity> getBlockEntityClass() {
        return BallBlockEntity.class;
    }

    public BlockEntityType<? extends BallBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends BallBlockEntity>)LoconauticsRegistries.BALL_BE.get();
    }
}
