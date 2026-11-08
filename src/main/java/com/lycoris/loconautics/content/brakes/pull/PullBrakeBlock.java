package com.lycoris.loconautics.content.brakes.pull;

import com.lycoris.loconautics.common.DyedBlock;
import com.lycoris.loconautics.common.ShiftPlacement;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PullBrakeBlock extends HorizontalDirectionalBlock implements IBE<PullBrakeBlockEntity>, IWrenchable, DyedBlock {
    public static final String BASE_COLOR = "brass";
    private static final VoxelShaper SHAPES = VoxelShaper.forHorizontal(Block.box(3.5, 1.0, 12.0, 12.5, 13.0, 16.0), Direction.NORTH);
    @Nullable
    private final DyeColor color;

    public PullBrakeBlock(Properties properties, @Nullable DyeColor color) {
        super(properties);
        this.color = color;
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
    }

    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new PullBrakeBlock(properties, this.color));
    }

    @Nullable
    @Override
    public DyeColor getColor() {
        return this.color;
    }

    public String colorName() {
        return this.color == null ? "brass" : this.color.getSerializedName();
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return (BlockState)((PullBrakeBlock)PullBrakeBlocks.BRAKES.get(dyed).get()).defaultBlockState().setValue(FACING, (Direction)state.getValue(FACING));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(FACING, ShiftPlacement.facing(context));
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get((Direction)state.getValue(FACING));
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        return this.paint(stack, state, level, pos, player);
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide && player.isLocalPlayer()) {
            this.withBlockEntityDo(level, pos, be -> PullBrakeHold.grab(level, player, pos, be.power()));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public Class<PullBrakeBlockEntity> getBlockEntityClass() {
        return PullBrakeBlockEntity.class;
    }

    public BlockEntityType<? extends PullBrakeBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends PullBrakeBlockEntity>)PullBrakeBlocks.PULL_BRAKE_BE.get();
    }
}
