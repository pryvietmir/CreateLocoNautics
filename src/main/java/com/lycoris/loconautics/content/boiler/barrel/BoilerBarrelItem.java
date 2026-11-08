package com.lycoris.loconautics.content.boiler.barrel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class BoilerBarrelItem extends BlockItem {
    public BoilerBarrelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            BlockPos above = context.getClickedPos().above();
            return !context.getLevel().getBlockState(above).canBeReplaced() ? null : BlockPlaceContext.at(context, above, Direction.UP);
        } else {
            return context;
        }
    }
}
