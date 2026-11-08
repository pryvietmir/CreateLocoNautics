package com.lycoris.loconautics.content.boiler.barrel;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.IAxisPipe;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "loconautics")
public final class BarrelPipeConnector {
    public static final int PIPE_GAP = 2;

    private BarrelPipeConnector() {
    }

    public static boolean hasConnector(BlockGetter level, BlockPos barrelPos, Direction face) {
        BlockState gap = level.getBlockState(barrelPos.relative(face));
        return gap.getBlock() instanceof BarrelConnectorBlock
            && gap.getValue(BarrelConnectorBlock.FACING) == face
            && pipeFitsFace(level.getBlockState(barrelPos.relative(face, 2)), face);
    }

    @Nullable
    public static Axis bodyAxis(BlockState state) {
        if (state.getBlock() instanceof BoilerBarrelBlock) {
            return ((Direction)state.getValue(BoilerBarrelBlock.FACING)).getAxis();
        }

        if (!state.is(BoilerTags.BOILER_BODY)) {
            return null;
        }

        if (state.hasProperty(BlockStateProperties.FACING)) {
            return ((Direction)state.getValue(BlockStateProperties.FACING)).getAxis();
        }

        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return ((Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING)).getAxis();
        }

        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("axis") && state.getValue(property) instanceof Axis axis) {
                return axis;
            }
        }

        return null;
    }

    public static boolean isFluidPipe(Block block) {
        return block instanceof FluidPipeBlock || block instanceof EncasedPipeBlock || block instanceof PumpBlock || block instanceof IAxisPipe;
    }

    public static boolean pipeFitsFace(BlockState pipe, Direction face) {
        return !isFluidPipe(pipe.getBlock())
            ? false
            : !(pipe.getBlock() instanceof PumpBlock) || ((Direction)pipe.getValue(PumpBlock.FACING)).getAxis() == face.getAxis();
    }

    public static boolean ensureConnector(Level level, BlockPos barrelPos, Direction face) {
        if (hasConnector(level, barrelPos, face)) {
            return true;
        }

        if (!pipeFitsFace(level.getBlockState(barrelPos.relative(face, 2)), face)) {
            return false;
        }

        BlockPos connectorPos = barrelPos.relative(face);
        if (!slotFreeForConnector(level, connectorPos)) {
            return false;
        }

        placeConnector(level, connectorPos, face);
        return true;
    }

    private static boolean slotFreeForConnector(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() || isFluidPipe(state.getBlock());
    }

    private static void trace(Level level, String what, @Nullable Player player, BlockPos pos, @Nullable Direction face) {
        LoconauticsConstants.LOGGER
            .debug(
                "[issue270] {} side={} sneak={} pos={} face={}",
                new Object[]{what, level.isClientSide() ? "client" : "server", player != null && player.isShiftKeyDown(), pos, face}
            );
    }

    private static void placeConnector(Level level, BlockPos pos, Direction toPipe) {
        trace(level, "connector placed facing " + toPipe, null, pos, toPipe);
        if (!level.getBlockState(pos).canBeReplaced()) {
            level.destroyBlock(pos, true);
        }

        BarrelConnectorBlock.place(level, pos, toPipe);
    }

    @SubscribeEvent
    public static void onRightClickBlock(RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player != null && stack.getItem() instanceof BlockItem item && isFluidPipe(item.getBlock())) {
            Level level = event.getLevel();
            BlockPos barrelPos = event.getPos();
            Direction face = event.getFace();
            if (face != null && barrelSide(level, barrelPos, face)) {
                BlockPos pipePos = barrelPos.relative(face, 2);
                BlockPos connectorPos = barrelPos.relative(face);
                if (level.getBlockState(pipePos).canBeReplaced() && slotFreeForConnector(level, connectorPos)) {
                    trace(level, "click handled", player, barrelPos, face);
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pipePos), face.getOpposite(), pipePos, false);
                    item.place(new BlockPlaceContext(player, event.getHand(), stack, hit));
                    if (!level.isClientSide()) {
                        ensureConnector(level, barrelPos, face);
                    }
                } else {
                    trace(level, "click skipped, slots " + level.getBlockState(pipePos) + " and " + level.getBlockState(connectorPos), player, barrelPos, face);
                }
            } else {
                trace(level, "click skipped, not a barrel side", player, barrelPos, face);
            }
        }
    }

    @SubscribeEvent
    public static void onPlaced(EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            BlockPos pos = event.getPos();
            BlockState placed = event.getPlacedBlock();
            if (isFluidPipe(placed.getBlock())) {
                trace(level, "pipe placed " + placed.getBlock(), event.getEntity() instanceof Player p ? p : null, pos, null);
            }

            Axis length = bodyAxis(placed);
            if (length != null) {
                for (Direction face : Direction.values()) {
                    if (face.getAxis() != length) {
                        ensureConnector(level, pos, face);
                    }
                }
            } else if (isFluidPipe(placed.getBlock())) {
                for (Direction toBarrel : Direction.values()) {
                    if (barrelSide(level, pos.relative(toBarrel, 2), toBarrel)
                        && pipeFitsFace(placed, toBarrel)
                        && slotFreeForConnector(level, pos.relative(toBarrel))) {
                        placeConnector(level, pos.relative(toBarrel), toBarrel.getOpposite());
                        return;
                    }

                    if (barrelSide(level, pos.relative(toBarrel), toBarrel)
                        && pipeFitsFace(placed, toBarrel)
                        && isFluidPipe(level.getBlockState(pos.relative(toBarrel.getOpposite())).getBlock())) {
                        placeConnector(level, pos, toBarrel.getOpposite());
                        return;
                    }
                }
            }
        }
    }

    private static boolean barrelSide(Level level, BlockPos barrelPos, Direction toBarrel) {
        Axis length = bodyAxis(level.getBlockState(barrelPos));
        return length != null && toBarrel.getAxis() != length;
    }
}
