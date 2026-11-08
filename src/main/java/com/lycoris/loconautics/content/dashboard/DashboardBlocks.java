package com.lycoris.loconautics.content.dashboard;

import com.lycoris.loconautics.foundation.LoconauticsBlockProperties;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class DashboardBlocks {
    public static final DyeColor TAB_COLOR = DyeColor.RED;
    public static final Map<DyeColor, DeferredHolder<Block, DashboardBlock>> DASHBOARDS = registerDashboards();
    public static final Map<DyeColor, DeferredHolder<Item, BlockItem>> DASHBOARD_ITEMS = registerDashboardItems();
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DashboardBlockEntity>> DASHBOARD_BE = LoconauticsRegistries.BLOCK_ENTITIES
        .register(
            "dashboard", () -> Builder.of(DashboardBlocks::createBE, DASHBOARDS.values().stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null)
        );

    private DashboardBlocks() {
    }

    public static String dashboardName(DyeColor color) {
        return color.getSerializedName() + "_dashboard";
    }

    private static Map<DyeColor, DeferredHolder<Block, DashboardBlock>> registerDashboards() {
        Map<DyeColor, DeferredHolder<Block, DashboardBlock>> boards = new EnumMap<>(DyeColor.class);

        for (DyeColor color : DyeColor.values()) {
            boards.put(
                color,
                LoconauticsRegistries.BLOCKS
                    .register(dashboardName(color), () -> new DashboardBlock(LoconauticsBlockProperties.metallic().noOcclusion(), color))
            );
        }

        return Collections.unmodifiableMap(boards);
    }

    private static Map<DyeColor, DeferredHolder<Item, BlockItem>> registerDashboardItems() {
        Map<DyeColor, DeferredHolder<Item, BlockItem>> items = new EnumMap<>(DyeColor.class);

        for (DyeColor color : DyeColor.values()) {
            items.put(
                color,
                LoconauticsRegistries.ITEMS
                    .register(dashboardName(color), () -> new BlockItem((Block)DASHBOARDS.get(color).get(), new Properties().stacksTo(64)))
            );
        }

        return Collections.unmodifiableMap(items);
    }

    static DashboardBlockEntity createBE(BlockPos pos, BlockState state) {
        return new DashboardBlockEntity((BlockEntityType<?>)DASHBOARD_BE.get(), pos, state);
    }

    public static void init() {
    }
}
