package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.common.DyedBlock;
import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.content.frequency.FrequencyBindUtil;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalogControllerBlock extends HorizontalDirectionalBlock implements IWrenchable, DyedBlock, EntityBlock {
    private static final Logger LOGGER = LoggerFactory.getLogger("AnalogControllerBlock");
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    private final DyeColor color;

    public AnalogControllerBlock(Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
        this.registerDefaultState(
            (BlockState)((BlockState)((BlockState)((BlockState)this.defaultBlockState().setValue(FACING, Direction.NORTH)).setValue(POWER, 0))
                    .setValue(ACTIVE, false))
                .setValue(POWERED, false)
        );
    }

    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new AnalogControllerBlock(properties, this.color));
    }

    @Override
    public DyeColor getColor() {
        return this.color;
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return (BlockState)((BlockState)((BlockState)((BlockState)((AnalogControllerBlock)AnalogControllerBlocks.CONTROLLERS.get(dyed).get())
                        .defaultBlockState()
                        .setValue(FACING, (Direction)state.getValue(FACING)))
                    .setValue(POWER, (Integer)state.getValue(POWER)))
                .setValue(ACTIVE, (Boolean)state.getValue(ACTIVE)))
            .setValue(POWERED, (Boolean)state.getValue(POWERED));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(new Property[]{FACING, POWER, ACTIVE, POWERED}));
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState)this.defaultBlockState().setValue(FACING, ShiftPlacement.facing(ctx));
    }

    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return (Integer)state.getValue(POWER);
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.CONTROLS.get((Direction)state.getValue(FACING));
    }

    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.CONTROLS_COLLISION.get((Direction)state.getValue(FACING));
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (stack.getItem() instanceof WrenchItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        ItemInteractionResult painted = this.paint(stack, state, level, pos, player);
        if (painted != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return painted;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                AnalogControllerBlockEntity ace = resolveBlockEntity(level, pos);
                if (ace != null) {
                    sp.openMenu(ace, ace::sendToMenu);
                }
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (!level.isClientSide) {
                AnalogControllerBlockEntity ace = resolveBlockEntity(level, pos);
                if (ace != null) {
                    ace.toggleUser(player);
                }
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    @Nullable
    private static AnalogControllerBlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AnalogControllerBlockEntity ace) {
            return ace;
        } else {
            SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
            if (subLevel != null && subLevel.getLevel().getBlockEntity(pos) instanceof AnalogControllerBlockEntity ace) {
                return ace;
            }

            LOGGER.warn("[analog-block] resolveBlockEntity returned null for pos={}", pos);
            return null;
        }
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && FrequencyBindUtil.isBound(stack) && resolveBlockEntity(level, pos) instanceof AnalogControllerBlockEntity be) {
            RegistryAccess var12 = level.registryAccess();
            ItemStack[] left = FrequencyBindUtil.read(stack, var12, "Left");
            if (left != null) {
                be.setLeftFrequency(left[0], left[1]);
            }

            ItemStack[] right = FrequencyBindUtil.read(stack, var12, "Right");
            if (right != null) {
                be.setRightFrequency(right[0], right[1]);
            }

            ItemStack[] speed = FrequencyBindUtil.read(stack, var12, "Speed");
            if (speed != null) {
                be.setFrequency(speed[0], speed[1]);
            }

            ItemStack[] dir = FrequencyBindUtil.read(stack, var12, "Direction");
            if (dir != null) {
                be.setBackwardFrequency(dir[0], dir[1]);
            }
        }
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnalogControllerBlockEntity((BlockEntityType<?>)AnalogControllerBlocks.CONTROLLER_BE.get(), pos, state);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AnalogControllerBlocks.CONTROLLER_BE.get()) {
            return null;
        }

        BlockEntityTicker<AnalogControllerBlockEntity> ticker = AnalogControllerBlockEntity::tick;
        return (BlockEntityTicker<T>)(BlockEntityTicker<?>)ticker;
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            AnalogControllerBlockEntity ace = resolveBlockEntity(level, pos);
            if (ace != null) {
                ace.onRemoved();
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
