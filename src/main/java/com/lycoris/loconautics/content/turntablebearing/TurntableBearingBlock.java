package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics.ExtraKineticsBlock;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class TurntableBearingBlock
    extends DirectionalKineticBlock
    implements IBE<TurntableBearingBlockEntity>,
    IRotate,
    ExtraKineticsBlock,
    BlockSubLevelAssemblyListener {
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public TurntableBearingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)this.defaultBlockState().setValue(ASSEMBLED, false)).setValue(POWERED, false));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(new Property[]{ASSEMBLED}).add(new Property[]{POWERED}));
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!player.mayBuild()) {
            return ItemInteractionResult.FAIL;
        }

        if (!stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player.isShiftKeyDown()) {
            CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> this.withBlockEntityDo(level, pos, be -> this.displayScreen(be, player)));
            return ItemInteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            this.withBlockEntityDo(level, pos, be -> be.assembleNextTick = true);
        }

        return ItemInteractionResult.SUCCESS;
    }

    @OnlyIn(Dist.CLIENT)
    protected void displayScreen(TurntableBearingBlockEntity be, Player player) {
        if (player instanceof LocalPlayer) {
            ScreenOpener.open(new TurntableBearingScreen(be));
        }
    }

    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            if (!level.getBlockTicks().willTickThisTick(pos, this)) {
                level.scheduleTick(pos, this, 0);
            }
        }
    }

    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.withBlockEntityDo(level, pos, be -> be.onRedstoneUpdate(level.hasNeighborSignal(pos)));
    }

    public Axis getRotationAxis(BlockState state) {
        return ((Direction)state.getValue(FACING)).getAxis();
    }

    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        Direction facing = (Direction)state.getValue(FACING);
        return state.getValue(ASSEMBLED) ? face == facing.getOpposite() : face.getAxis() == facing.getAxis();
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ASSEMBLED) ? TurntableBearingShapes.BEARING_ASSEMBLED.get((Direction)state.getValue(FACING)) : Shapes.block();
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState rotated = this.getRotatedBlockState(state, context.getClickedFace());
        if (!rotated.canSurvive(level, pos)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            this.withBlockEntityDo(level, pos, TurntableBearingBlockEntity::disassemble);
        }

        rotated = this.getRotatedBlockState(level.getBlockState(pos), context.getClickedFace());
        KineticBlockEntity.switchToBlockState(level, pos, this.updateAfterWrenched(rotated, context));
        if (level.getBlockState(pos) != state) {
            IWrenchable.playRotateSound(level, pos);
        }

        return InteractionResult.SUCCESS;
    }

    public Class<TurntableBearingBlockEntity> getBlockEntityClass() {
        return TurntableBearingBlockEntity.class;
    }

    public BlockEntityType<? extends TurntableBearingBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends TurntableBearingBlockEntity>)LoconauticsRegistries.TURNTABLE_BEARING_BE.get();
    }

    @Override
    public IRotate getExtraKineticsRotationConfiguration() {
        return TurntableBearingBlockEntity.TurntableCogwheelBlockEntity.EXTRA_COGWHEEL_CONFIG;
    }

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        this.withBlockEntityDo(originLevel, oldPos, TurntableBearingBlockEntity::beforeAssembly);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        this.withBlockEntityDo(resultingLevel, newPos, TurntableBearingBlockEntity::associatePlateWithParent);
    }
}
