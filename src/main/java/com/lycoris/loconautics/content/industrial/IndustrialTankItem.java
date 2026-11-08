package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public class IndustrialTankItem extends BlockItem {
    public IndustrialTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (!result.consumesAction()) {
            return result;
        }

        this.tryMultiPlace(context);
        return result;
    }

    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (!super.canPlace(context, state)) {
            return false;
        }

        TankMultiblock.Fit fit = TankMultiblock.checkPlacement(context.getLevel(), List.of(context.getClickedPos()));
        if (fit == TankMultiblock.Fit.OK) {
            return true;
        }

        warn(context.getPlayer(), context.getLevel(), fit);
        return false;
    }

    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }

        CustomData data = (CustomData)stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            tag.remove("Luminosity");
            tag.remove("Size");
            tag.remove("MinY");
            tag.remove("Layers");
            tag.remove("Controller");
            if (tag.contains("TankContent")) {
                FluidStack fluid = FluidStack.parseOptional(server.registryAccess(), tag.getCompound("TankContent"));
                if (!fluid.isEmpty()) {
                    fluid.setAmount(Math.min(IndustrialTankBlockEntity.cellCapacity(), fluid.getAmount()));
                    tag.put("TankContent", fluid.saveOptional(server.registryAccess()));
                }
            }

            BlockEntity.addEntityType(tag, (BlockEntityType)IndustrialBlocks.TANK_BE.get());
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
        }

        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    private void tryMultiPlace(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.isShiftKeyDown()) {
            Direction face = context.getClickedFace();
            Level level = context.getLevel();
            BlockPos placedOnPos = context.getClickedPos().relative(face.getOpposite());
            BlockState placedOn = level.getBlockState(placedOnPos);
            if (placedOn.is((Block)IndustrialBlocks.TANK.get())) {
                if (placedOn.getValue(IndustrialTankBlock.AXIS) == face.getAxis()) {
                    if (!SymmetryWandItem.presentInHotbar(player)) {
                        TankMultiblock.Group group = TankMultiblock.collect(level, List.of(placedOnPos), Set.of());
                        if (group.complete()) {
                            List<BlockPos> missing = new ArrayList<>();

                            for (BlockPos cell : group.cells()) {
                                if (cell.get(face.getAxis()) == placedOnPos.get(face.getAxis())) {
                                    BlockPos target = cell.relative(face);
                                    BlockState existing = level.getBlockState(target);
                                    if (!existing.is((Block)IndustrialBlocks.TANK.get())) {
                                        if (!existing.canBeReplaced()) {
                                            return;
                                        }

                                        missing.add(target);
                                    }
                                }
                            }

                            if (!missing.isEmpty()) {
                                ItemStack stack = context.getItemInHand();
                                if (player.isCreative() || stack.getCount() >= missing.size()) {
                                    TankMultiblock.Fit fit = TankMultiblock.checkPlacement(level, missing);
                                    if (fit != TankMultiblock.Fit.OK) {
                                        warn(player, level, fit);
                                    } else {
                                        for (BlockPos target : missing) {
                                            BlockPlaceContext next = BlockPlaceContext.at(context, target, face);
                                            player.getPersistentData().putBoolean("SilenceTankSound", true);
                                            super.place(next);
                                            player.getPersistentData().remove("SilenceTankSound");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void warn(Player player, Level level, TankMultiblock.Fit fit) {
        if (player != null && !level.isClientSide) {
            String key = fit == TankMultiblock.Fit.TOO_BIG
                ? "block.loconautics.industrial_fluid_tank.too_big"
                : "block.loconautics.industrial_fluid_tank.mixed_fluids";
            player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
