package com.lycoris.loconautics.content.industrial;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

@EventBusSubscriber(modid = "loconautics")
public final class IndustrialBlocks {
    public static final float CAPACITY_FACTOR = 1.5F;
    public static final float RANGE_FACTOR = 1.5F;
    public static final DeferredHolder<Block, IndustrialPipeBlock> PIPE = LoconauticsRegistries.BLOCKS
        .register("industrial_pipe", () -> new IndustrialPipeBlock(copperish().forceSolidOff()));
    public static final DeferredHolder<Item, BlockItem> PIPE_ITEM = LoconauticsRegistries.ITEMS
        .register("industrial_pipe", () -> new BlockItem((Block)PIPE.get(), new Properties()));
    public static final DeferredHolder<Block, IndustrialGlassPipeBlock> GLASS_PIPE = LoconauticsRegistries.BLOCKS
        .register("industrial_glass_pipe", () -> new IndustrialGlassPipeBlock(copperish().noOcclusion()));
    public static final DeferredHolder<Block, PumpBlock> PUMP = LoconauticsRegistries.BLOCKS
        .register("industrial_pump", () -> new PumpBlock(copperish().mapColor(MapColor.STONE)));
    public static final DeferredHolder<Item, BlockItem> PUMP_ITEM = LoconauticsRegistries.ITEMS
        .register("industrial_pump", () -> new BlockItem((Block)PUMP.get(), new Properties()));
    public static final DeferredHolder<Block, SmartFluidPipeBlock> SMART_PIPE = LoconauticsRegistries.BLOCKS
        .register("industrial_smart_pipe", () -> new SmartFluidPipeBlock(copperish().mapColor(MapColor.TERRACOTTA_YELLOW)));
    public static final DeferredHolder<Item, BlockItem> SMART_PIPE_ITEM = LoconauticsRegistries.ITEMS
        .register("industrial_smart_pipe", () -> new BlockItem((Block)SMART_PIPE.get(), new Properties()));
    public static final DeferredHolder<Block, FluidValveBlock> VALVE = LoconauticsRegistries.BLOCKS
        .register("industrial_valve", () -> new FluidValveBlock(copperish()));
    public static final DeferredHolder<Item, BlockItem> VALVE_ITEM = LoconauticsRegistries.ITEMS
        .register("industrial_valve", () -> new BlockItem((Block)VALVE.get(), new Properties()));
    public static final DeferredHolder<Block, IndustrialTankBlock> TANK = LoconauticsRegistries.BLOCKS
        .register("industrial_fluid_tank", () -> new IndustrialTankBlock(copperish().noOcclusion().isRedstoneConductor((s, l, p) -> true)));
    public static final DeferredHolder<Item, IndustrialTankItem> TANK_ITEM = LoconauticsRegistries.ITEMS
        .register("industrial_fluid_tank", () -> new IndustrialTankItem((Block)TANK.get(), new Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialTankBlockEntity>> TANK_BE = LoconauticsRegistries.BLOCK_ENTITIES
        .register("industrial_fluid_tank", () -> Builder.of(IndustrialBlocks::createTankBE, new Block[]{(Block)TANK.get()}).build(null));

    public static int pumpRange(BlockState pump, int baseRange) {
        return pump.is((Block)PUMP.get()) ? (int)(baseRange * 1.5F) : baseRange;
    }

    private static net.minecraft.world.level.block.state.BlockBehaviour.Properties copperish() {
        return net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_LIGHT_GRAY)
            .strength(3.0F)
            .sound(SoundType.COPPER)
            .requiresCorrectToolForDrops();
    }

    private IndustrialBlocks() {
    }

    private static IndustrialTankBlockEntity createTankBE(BlockPos pos, BlockState state) {
        return new IndustrialTankBlockEntity((BlockEntityType<?>)TANK_BE.get(), pos, state);
    }

    public static void init() {
    }

    @SubscribeEvent
    public static void addToCreateBlockEntities(BlockEntityTypeAddBlocksEvent event) {
        event.modify((BlockEntityType)AllBlockEntityTypes.FLUID_PIPE.get(), new Block[]{(Block)PIPE.get()});
        event.modify((BlockEntityType)AllBlockEntityTypes.GLASS_FLUID_PIPE.get(), new Block[]{(Block)GLASS_PIPE.get()});
        event.modify((BlockEntityType)AllBlockEntityTypes.MECHANICAL_PUMP.get(), new Block[]{(Block)PUMP.get()});
        event.modify((BlockEntityType)AllBlockEntityTypes.SMART_FLUID_PIPE.get(), new Block[]{(Block)SMART_PIPE.get()});
        event.modify((BlockEntityType)AllBlockEntityTypes.FLUID_VALVE.get(), new Block[]{(Block)VALVE.get()});
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(FluidHandler.BLOCK, (BlockEntityType)TANK_BE.get(), (be, context) -> be.getFluidHandler());
    }
}
