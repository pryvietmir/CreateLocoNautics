package com.lycoris.loconautics.registry;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlocks;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerMenu;
import com.lycoris.loconautics.content.ball.BallBlock;
import com.lycoris.loconautics.content.ball.BallBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleMenu;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.brakes.HandbrakeBlock;
import com.lycoris.loconautics.content.brakes.pull.PullBrakeBlockEntity;
import com.lycoris.loconautics.content.brakes.pull.PullBrakeBlocks;
import com.lycoris.loconautics.content.buffer.BufferBlocks;
import com.lycoris.loconautics.content.casingpicker.BogeyChoice;
import com.lycoris.loconautics.content.casingpicker.BogeyItem;
import com.lycoris.loconautics.content.cerisene.CeriseneBlocks;
import com.lycoris.loconautics.content.combustion.SmallEngineBlocks;
import com.lycoris.loconautics.content.conductorshat.ConductorsHats;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import com.lycoris.loconautics.content.dashboard.DashboardBlocks;
import com.lycoris.loconautics.content.ghostwheel.GhostWheelBlock;
import com.lycoris.loconautics.content.hatch.FluidHatchBlock;
import com.lycoris.loconautics.content.hatch.FluidHatchBlockEntity;
import com.lycoris.loconautics.content.headstock.CopycatHeadstockBlock;
import com.lycoris.loconautics.content.headstock.CopycatHeadstockBlockEntity;
import com.lycoris.loconautics.content.hose.HoseItem;
import com.lycoris.loconautics.content.hose.link.HoseConnectorBlock;
import com.lycoris.loconautics.content.hose.link.HoseConnectorBlockEntity;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlock;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.hose.spray.HoseSprayOptions;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlock;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.content.industrial.IndustrialBlocks;
import com.lycoris.loconautics.content.knuckle.KnuckleBlock;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.content.relocator.CreativeTrainRelocatorItem;
import com.lycoris.loconautics.content.screwlink.ScrewLinkBlock;
import com.lycoris.loconautics.content.screwlink.ScrewLinkBlockEntity;
import com.lycoris.loconautics.content.smokestack.SmokestackBlock;
import com.lycoris.loconautics.content.smokestack.SmokestackBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableItem;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.content.transmission.TransmissionMenu;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlock;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingBlockEntity;
import com.lycoris.loconautics.content.turntablebearing.TurntablePlateBlock;
import com.lycoris.loconautics.content.turntablebearing.TurntablePlateBlockEntity;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import dev.simulated_team.simulated.index.SimBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

public final class LoconauticsRegistries {
    public static final Blocks BLOCKS = DeferredRegister.createBlocks("loconautics");
    public static final Items ITEMS = DeferredRegister.createItems("loconautics");
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "loconautics");
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "loconautics");
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "loconautics");
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "loconautics");
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, "loconautics");
    public static final DeferredHolder<ParticleType<?>, ParticleType<HoseSprayOptions>> HOSE_WATER_SPRAY = PARTICLE_TYPES.register(
        "hose_water_spray", () -> new ParticleType<HoseSprayOptions>(false) {
            public MapCodec<HoseSprayOptions> codec() {
                return HoseSprayOptions.codec(this);
            }

            public StreamCodec<? super RegistryFriendlyByteBuf, HoseSprayOptions> streamCodec() {
                return HoseSprayOptions.streamCodec(this);
            }
        }
    );
    public static final DeferredHolder<ParticleType<?>, ParticleType<SmokeOptions>> SMOKE = PARTICLE_TYPES.register(
        "smoke", () -> new ParticleType<SmokeOptions>(false) {
            public MapCodec<SmokeOptions> codec() {
                return SmokeOptions.codec(this);
            }

            public StreamCodec<? super RegistryFriendlyByteBuf, SmokeOptions> streamCodec() {
                return SmokeOptions.streamCodec(this);
            }
        }
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BogeyChoice>> BOGEY_CHOICE = DATA_COMPONENTS.register(
        "bogey_choice", () -> DataComponentType.<BogeyChoice>builder().persistent(BogeyChoice.CODEC).networkSynchronized(BogeyChoice.STREAM_CODEC).build()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> LOCOMOTIVE_GAUGE_TARGET = DATA_COMPONENTS.register(
        "locomotive_gauge_target", () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> TURNTABLE_SIGNAL_TARGET = DATA_COMPONENTS.register(
        "turntable_signal_target", () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> VALVE_TARGET = DATA_COMPONENTS.register(
        "valve_target", () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> BUTTON_TARGET = DATA_COMPONENTS.register(
        "button_target", () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> SWITCH_TARGET = DATA_COMPONENTS.register(
        "switch_target", () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> LEVER_TARGET = DATA_COMPONENTS.register(
        "lever_target", () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> STEEL_CABLE_FIRST_CONNECTION = DATA_COMPONENTS.register(
        "steel_cable_first_connection", () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build()
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GaugeMetric>> LOCOMOTIVE_GAUGE_METRIC = DATA_COMPONENTS.register(
        "locomotive_gauge_metric", () -> DataComponentType.<GaugeMetric>builder().persistent(GaugeMetric.CODEC).networkSynchronized(GaugeMetric.STREAM_CODEC).build()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnalogControllerBlockEntity>> ANALOG_CONTROLLER_BE = AnalogControllerBlocks.CONTROLLER_BE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PullBrakeBlockEntity>> PULL_BRAKE_BE = PullBrakeBlocks.PULL_BRAKE_BE;
    public static final DeferredHolder<MenuType<?>, MenuType<AnalogControllerMenu>> ANALOG_CONTROLLER_MENU = MENUS.register(
        "analog_controller", () -> IMenuTypeExtension.create(LoconauticsRegistries::createAnalogControllerMenu)
    );
    public static final DeferredHolder<Block, BearingAxleBlock> BEARING_AXLE = BLOCKS.register(
        "bearing_axle",
        () -> new BearingAxleBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()
        )
    );
    public static final DeferredHolder<Item, BlockItem> BEARING_AXLE_ITEM = ITEMS.register(
        "bearing_axle", () -> new BlockItem((Block)BEARING_AXLE.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BearingAxleBlockEntity>> BEARING_AXLE_BE = BLOCK_ENTITIES.register(
        "bearing_axle", () -> Builder.of(LoconauticsRegistries::createBearingAxleBE, new Block[]{(Block)BEARING_AXLE.get()}).build(null)
    );
    public static final DeferredHolder<MenuType<?>, MenuType<BearingAxleMenu>> BEARING_AXLE_MENU = MENUS.register(
        "bearing_axle", () -> IMenuTypeExtension.create(LoconauticsRegistries::createBearingAxleMenu)
    );
    public static final DeferredHolder<MenuType<?>, MenuType<PanelFrequencyMenu>> PANEL_FREQUENCY_MENU = MENUS.register(
        "panel_frequency", () -> IMenuTypeExtension.create(LoconauticsRegistries::createPanelFrequencyMenu)
    );
    public static final DeferredHolder<Block, TurntableBearingBlock> TURNTABLE_BEARING = BLOCKS.register(
        "turntable_bearing",
        () -> new TurntableBearingBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()
        )
    );
    public static final DeferredHolder<Item, BlockItem> TURNTABLE_BEARING_ITEM = ITEMS.register(
        "turntable_bearing", () -> new BlockItem((Block)TURNTABLE_BEARING.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurntableBearingBlockEntity>> TURNTABLE_BEARING_BE = BLOCK_ENTITIES.register(
        "turntable_bearing", () -> Builder.of(LoconauticsRegistries::createTurntableBearingBE, new Block[]{(Block)TURNTABLE_BEARING.get()}).build(null)
    );
    public static final DeferredHolder<Block, TurntablePlateBlock> TURNTABLE_PLATE = BLOCKS.register(
        "turntable_plate",
        () -> new TurntablePlateBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()
        )
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurntablePlateBlockEntity>> TURNTABLE_PLATE_BE = BLOCK_ENTITIES.register(
        "turntable_plate", () -> Builder.of(LoconauticsRegistries::createTurntablePlateBE, new Block[]{(Block)TURNTABLE_PLATE.get()}).build(null)
    );
    public static final DeferredHolder<Block, KnuckleBlock> KNUCKLE = BLOCKS.register(
        "knuckle",
        () -> new KnuckleBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
        )
    );
    public static final DeferredHolder<Item, BlockItem> KNUCKLE_ITEM = ITEMS.register(
        "knuckle", () -> new BlockItem((Block)KNUCKLE.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KnuckleBlockEntity>> KNUCKLE_BE = BLOCK_ENTITIES.register(
        "knuckle", () -> Builder.of(LoconauticsRegistries::createKnuckleBE, new Block[]{(Block)KNUCKLE.get()}).build(null)
    );
    public static final DeferredHolder<Block, ScrewLinkBlock> SCREW_LINK = BLOCKS.register(
        "screw_link",
        () -> new ScrewLinkBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
        )
    );
    public static final DeferredHolder<Item, BlockItem> SCREW_LINK_ITEM = ITEMS.register(
        "screw_link", () -> new BlockItem((Block)SCREW_LINK.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScrewLinkBlockEntity>> SCREW_LINK_BE = BLOCK_ENTITIES.register(
        "screw_link", () -> Builder.of(LoconauticsRegistries::createScrewLinkBE, new Block[]{(Block)SCREW_LINK.get()}).build(null)
    );
    public static final DeferredHolder<Block, CopycatHeadstockBlock> COPYCAT_HEADSTOCK = BLOCKS.register(
        "copycat_headstock",
        () -> new CopycatHeadstockBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
        )
    );
    public static final DeferredHolder<Item, BlockItem> COPYCAT_HEADSTOCK_ITEM = ITEMS.register(
        "copycat_headstock", () -> new BlockItem((Block)COPYCAT_HEADSTOCK.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopycatHeadstockBlockEntity>> COPYCAT_HEADSTOCK_BE = BLOCK_ENTITIES.register(
        "copycat_headstock", () -> Builder.of(LoconauticsRegistries::createCopycatHeadstockBE, new Block[]{(Block)COPYCAT_HEADSTOCK.get()}).build(null)
    );
    public static final DeferredHolder<Block, HandbrakeBlock> HANDBRAKE = BLOCKS.register(
        "handbrake",
        () -> new HandbrakeBlock(Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops())
    );
    public static final DeferredHolder<Item, BlockItem> HANDBRAKE_ITEM = ITEMS.register(
        "handbrake", () -> new BlockItem((Block)HANDBRAKE.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<Block, GhostWheelBlock> GHOST_WHEEL = BLOCKS.register(
        "ghost_wheel",
        () -> new GhostWheelBlock(Properties.of().mapColor(MapColor.NONE).noOcclusion().noLootTable().sound(SoundType.EMPTY).strength(-1.0F, 3600000.0F))
    );
    public static final DeferredHolder<Block, TransmissionBlock> TRANSMISSION = BLOCKS.register(
        "transmission",
        () -> new TransmissionBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()
        )
    );
    public static final DeferredHolder<Item, BlockItem> TRANSMISSION_ITEM = ITEMS.register(
        "transmission", () -> new BlockItem((Block)TRANSMISSION.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransmissionBlockEntity>> TRANSMISSION_BE = BLOCK_ENTITIES.register(
        "transmission", () -> Builder.of(LoconauticsRegistries::createTransmissionBE, new Block[]{(Block)TRANSMISSION.get()}).build(null)
    );
    public static final DeferredHolder<MenuType<?>, MenuType<TransmissionMenu>> TRANSMISSION_MENU = MENUS.register(
        "transmission", () -> IMenuTypeExtension.create(LoconauticsRegistries::createTransmissionMenu)
    );
    public static final DeferredHolder<Item, SteelCableItem> STEEL_CABLE = ITEMS.register(
        "steel_cable", () -> new SteelCableItem(new net.minecraft.world.item.Item.Properties().stacksTo(16))
    );
    public static final DeferredHolder<Item, Item> BOILER_TUBES = ITEMS.register("boiler_tubes", () -> new Item(new net.minecraft.world.item.Item.Properties()));
    public static final DeferredHolder<Item, Item> INDUSTRIAL_IRON_ROD = ITEMS.register(
        "industrial_iron_rod", () -> new Item(new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredHolder<Item, Item> VALVE_GEAR = ITEMS.register("valve_gear", () -> new Item(new net.minecraft.world.item.Item.Properties()));
    public static final DeferredHolder<Item, Item> INDUSTRIAL_IRON_SHEET = ITEMS.register(
        "industrial_iron_sheet", () -> new Item(new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<Item, Item> UNPROCESSED_REINFORCED_IRON_SHEET = ITEMS.register(
        "unprocessed_reinforced_iron_sheet", () -> new Item(new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<Item, Item> BOGEY = ITEMS.register("bogey", () -> new BogeyItem(new net.minecraft.world.item.Item.Properties()));
    public static final DeferredHolder<Item, Item> CREATIVE_TRAIN_RELOCATOR = ITEMS.register(
        "creative_train_relocator", () -> new CreativeTrainRelocatorItem(new net.minecraft.world.item.Item.Properties().stacksTo(1))
    );
    public static final DeferredHolder<Block, SmokestackBlock> SMOKESTACK = BLOCKS.register(
        "smokestack",
        () -> new SmokestackBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()
        )
    );
    public static final DeferredHolder<Item, BlockItem> SMOKESTACK_ITEM = ITEMS.register(
        "smokestack", () -> new BlockItem((Block)SMOKESTACK.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmokestackBlockEntity>> SMOKESTACK_BE = BLOCK_ENTITIES.register(
        "smokestack", () -> Builder.of(SmokestackBlockEntity::new, new Block[]{(Block)SMOKESTACK.get()}).build(null)
    );
    public static final DeferredHolder<Block, HoseWinchBlock> HOSE_WINCH = BLOCKS.register(
        "hose_winch",
        () -> new HoseWinchBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()
        )
    );
    public static final DeferredHolder<Item, BlockItem> HOSE_WINCH_ITEM = ITEMS.register(
        "hose_winch", () -> new BlockItem((Block)HOSE_WINCH.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoseWinchBlockEntity>> HOSE_WINCH_BE = BLOCK_ENTITIES.register(
        "hose_winch", () -> Builder.of(LoconauticsRegistries::createHoseWinchBE, new Block[]{(Block)HOSE_WINCH.get()}).build(null)
    );
    public static final DeferredHolder<Block, HoseNozzleBlock> HOSE_NOZZLE = BLOCKS.register(
        "hose_nozzle",
        () -> new HoseNozzleBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()
        )
    );
    public static final DeferredHolder<Item, BlockItem> HOSE_NOZZLE_ITEM = ITEMS.register(
        "hose_nozzle", () -> new BlockItem((Block)HOSE_NOZZLE.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoseNozzleBlockEntity>> HOSE_NOZZLE_BE = BLOCK_ENTITIES.register(
        "hose_nozzle", () -> Builder.of(LoconauticsRegistries::createHoseNozzleBE, new Block[]{(Block)HOSE_NOZZLE.get()}).build(null)
    );
    public static final DeferredHolder<Block, BallBlock> BALL = BLOCKS.register(
        "ball", () -> new BallBlock(Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5F).sound(SoundType.WOOL))
    );
    public static final DeferredHolder<Item, BlockItem> BALL_ITEM = ITEMS.register(
        "ball", () -> new BlockItem((Block)BALL.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BallBlockEntity>> BALL_BE = BLOCK_ENTITIES.register(
        "ball", () -> Builder.of(LoconauticsRegistries::createBallBE, new Block[]{(Block)BALL.get()}).build(null)
    );
    public static final DeferredHolder<Block, HoseConnectorBlock> HOSE_CONNECTOR = BLOCKS.register(
        "hose_connector", () -> new HoseConnectorBlock(Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion())
    );
    public static final DeferredHolder<Item, BlockItem> HOSE_CONNECTOR_ITEM = ITEMS.register(
        "hose_connector", () -> new BlockItem((Block)HOSE_CONNECTOR.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoseConnectorBlockEntity>> HOSE_CONNECTOR_BE = BLOCK_ENTITIES.register(
        "hose_connector", () -> Builder.of(LoconauticsRegistries::createHoseConnectorBE, new Block[]{(Block)HOSE_CONNECTOR.get()}).build(null)
    );
    public static final DeferredHolder<Block, FluidHatchBlock> FLUID_HATCH = BLOCKS.register(
        "fluid_hatch_gray",
        () -> new FluidHatchBlock(
            Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().requiresCorrectToolForDrops()
        )
    );
    public static final DeferredHolder<Item, BlockItem> FLUID_HATCH_ITEM = ITEMS.register(
        "fluid_hatch_gray", () -> new BlockItem((Block)FLUID_HATCH.get(), new net.minecraft.world.item.Item.Properties().stacksTo(64))
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidHatchBlockEntity>> FLUID_HATCH_BE = BLOCK_ENTITIES.register(
        "fluid_hatch", () -> Builder.of(LoconauticsRegistries::createFluidHatchBE, new Block[]{(Block)FLUID_HATCH.get()}).build(null)
    );
    public static final DeferredHolder<Item, HoseItem> HOSE = ITEMS.register(
        "hose", () -> new HoseItem(new net.minecraft.world.item.Item.Properties().stacksTo(16))
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOCONAUTICS_TAB = CREATIVE_TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.loconautics.main"))
            .icon(() -> new ItemStack((ItemLike)AnalogControllerBlocks.CONTROLLER_ITEMS.get(AnalogControllerBlocks.TAB_COLOR).get()))
            .displayItems(
                (params, output) -> {
                    AnalogControllerBlocks.CONTROLLER_ITEMS
                        .forEach(
                            (color, item) -> output.accept(
                                new ItemStack((ItemLike)item.get()),
                                color == AnalogControllerBlocks.TAB_COLOR ? TabVisibility.PARENT_AND_SEARCH_TABS : TabVisibility.SEARCH_TAB_ONLY
                            )
                        );
                    output.accept((ItemLike)BEARING_AXLE_ITEM.get());
                    output.accept((ItemLike)TRANSMISSION_ITEM.get());
                    output.accept(AllBlocks.TRACK_STATION.asStack());
                    output.accept(AllBlocks.TRACK_SIGNAL.asStack());
                    output.accept(AllBlocks.TRACK_OBSERVER.asStack());
                    output.accept(AllBlocks.PACKAGE_POSTBOXES.get(DyeColor.RED).asStack());
                    output.accept(AllBlocks.TRACK.asStack());
                    output.accept(AllBlocks.RAILWAY_CASING.asStack());
                    output.accept(AllItems.SCHEDULE.asStack());
                    output.accept((ItemLike)HANDBRAKE_ITEM.get());
                    PullBrakeBlocks.BRAKE_ITEMS
                        .forEach(
                            (color, item) -> output.accept(
                                new ItemStack((ItemLike)item.get()),
                                color == PullBrakeBlocks.TAB_COLOR ? TabVisibility.PARENT_AND_SEARCH_TABS : TabVisibility.SEARCH_TAB_ONLY
                            )
                        );
                    output.accept((ItemLike)TURNTABLE_BEARING_ITEM.get());
                    output.accept(SimBlocks.ROPE_CONNECTOR);
                    output.accept((ItemLike)STEEL_CABLE.get());
                    output.accept((ItemLike)COPYCAT_HEADSTOCK_ITEM.get());
                    output.accept((ItemLike)KNUCKLE_ITEM.get());
                    BufferBlocks.BUFFER_ITEMS
                        .forEach(
                            (color, item) -> output.accept(
                                new ItemStack((ItemLike)item.get()),
                                color == BufferBlocks.TAB_COLOR ? TabVisibility.PARENT_AND_SEARCH_TABS : TabVisibility.SEARCH_TAB_ONLY
                            )
                        );
                    output.accept((ItemLike)SCREW_LINK_ITEM.get());
                    DashboardBlocks.DASHBOARD_ITEMS
                        .forEach(
                            (color, item) -> output.accept(
                                new ItemStack((ItemLike)item.get()),
                                color == DashboardBlocks.TAB_COLOR ? TabVisibility.PARENT_AND_SEARCH_TABS : TabVisibility.SEARCH_TAB_ONLY
                            )
                        );
                    output.accept((ItemLike)HOSE_WINCH_ITEM.get());
                    output.accept((ItemLike)HOSE_NOZZLE_ITEM.get());
                    output.accept((ItemLike)HOSE_CONNECTOR_ITEM.get());
                    output.accept((ItemLike)HOSE.get());
                    output.accept((ItemLike)FLUID_HATCH_ITEM.get());
                    output.accept((ItemLike)INDUSTRIAL_IRON_SHEET.get());
                    output.accept((ItemLike)INDUSTRIAL_IRON_ROD.get());
                    output.accept((ItemLike)IndustrialBlocks.PIPE_ITEM.get());
                    output.accept((ItemLike)IndustrialBlocks.SMART_PIPE_ITEM.get());
                    output.accept((ItemLike)IndustrialBlocks.PUMP_ITEM.get());
                    output.accept((ItemLike)IndustrialBlocks.VALVE_ITEM.get());
                    output.accept((ItemLike)IndustrialBlocks.TANK_ITEM.get());
                    output.accept((ItemLike)CREATIVE_TRAIN_RELOCATOR.get());
                    ConductorsHats.HATS
                        .forEach(
                            (color, item) -> output.accept(
                                new ItemStack((ItemLike)item.get()),
                                color == ConductorsHats.TAB_COLOR ? TabVisibility.PARENT_AND_SEARCH_TABS : TabVisibility.SEARCH_TAB_ONLY
                            )
                        );
                    output.accept((ItemLike)BoilerBlocks.FIREBOX_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.BOILER_BODY_ITEM.get());
                    output.accept((ItemLike)SMOKESTACK_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.STEAM_TAKEOFF_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.SMART_SAFETY_VALVE_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.FIREBOX_STOCKER_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.LOCOMOTIVE_GAUGE_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.VALVE_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.GAUGE_BUTTON_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.GAUGE_LEVER_ITEM.get());
                    output.accept((ItemLike)BoilerBlocks.GAUGE_SWITCH_ITEM.get());
                    output.accept((ItemLike)BOILER_TUBES.get());
                    output.accept((ItemLike)VALVE_GEAR.get());
                    SmallEngineBlocks.ENGINE_ITEMS
                        .forEach(
                            (color, item) -> output.accept(
                                new ItemStack((ItemLike)item.get()),
                                color == SmallEngineBlocks.TAB_COLOR ? TabVisibility.PARENT_AND_SEARCH_TABS : TabVisibility.SEARCH_TAB_ONLY
                            )
                        );
                    output.accept((ItemLike)CeriseneBlocks.RAW_CERISENE.get());
                    output.accept((ItemLike)CeriseneBlocks.CERISENE_BLOCK_ITEM.get());
                    output.accept((ItemLike)CeriseneBlocks.CERISENE_BUCKET.get());
                }
            )
            .build()
    );

    static AnalogControllerMenu createAnalogControllerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new AnalogControllerMenu((MenuType<?>)ANALOG_CONTROLLER_MENU.get(), id, inv, buf);
    }

    static BearingAxleBlockEntity createBearingAxleBE(BlockPos pos, BlockState state) {
        return new BearingAxleBlockEntity((BlockEntityType<?>)BEARING_AXLE_BE.get(), pos, state);
    }

    static BearingAxleMenu createBearingAxleMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new BearingAxleMenu((MenuType<?>)BEARING_AXLE_MENU.get(), id, inv, buf);
    }

    static PanelFrequencyMenu createPanelFrequencyMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new PanelFrequencyMenu((MenuType<?>)PANEL_FREQUENCY_MENU.get(), id, inv, buf);
    }

    static TurntableBearingBlockEntity createTurntableBearingBE(BlockPos pos, BlockState state) {
        return new TurntableBearingBlockEntity((BlockEntityType<?>)TURNTABLE_BEARING_BE.get(), pos, state);
    }

    static TurntablePlateBlockEntity createTurntablePlateBE(BlockPos pos, BlockState state) {
        return new TurntablePlateBlockEntity((BlockEntityType<?>)TURNTABLE_PLATE_BE.get(), pos, state);
    }

    static ScrewLinkBlockEntity createScrewLinkBE(BlockPos pos, BlockState state) {
        return new ScrewLinkBlockEntity((BlockEntityType<?>)SCREW_LINK_BE.get(), pos, state);
    }

    static KnuckleBlockEntity createKnuckleBE(BlockPos pos, BlockState state) {
        return new KnuckleBlockEntity((BlockEntityType<?>)KNUCKLE_BE.get(), pos, state);
    }

    private static CopycatHeadstockBlockEntity createCopycatHeadstockBE(BlockPos pos, BlockState state) {
        return new CopycatHeadstockBlockEntity((BlockEntityType<?>)COPYCAT_HEADSTOCK_BE.get(), pos, state);
    }

    static TransmissionBlockEntity createTransmissionBE(BlockPos pos, BlockState state) {
        return new TransmissionBlockEntity((BlockEntityType<?>)TRANSMISSION_BE.get(), pos, state);
    }

    static TransmissionMenu createTransmissionMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new TransmissionMenu((MenuType<?>)TRANSMISSION_MENU.get(), id, inv, buf);
    }

    static HoseWinchBlockEntity createHoseWinchBE(BlockPos pos, BlockState state) {
        return new HoseWinchBlockEntity((BlockEntityType<?>)HOSE_WINCH_BE.get(), pos, state);
    }

    static HoseConnectorBlockEntity createHoseConnectorBE(BlockPos pos, BlockState state) {
        return new HoseConnectorBlockEntity((BlockEntityType<?>)HOSE_CONNECTOR_BE.get(), pos, state);
    }

    static BallBlockEntity createBallBE(BlockPos pos, BlockState state) {
        return new BallBlockEntity((BlockEntityType<?>)BALL_BE.get(), pos, state);
    }

    static HoseNozzleBlockEntity createHoseNozzleBE(BlockPos pos, BlockState state) {
        return new HoseNozzleBlockEntity((BlockEntityType<?>)HOSE_NOZZLE_BE.get(), pos, state);
    }

    static FluidHatchBlockEntity createFluidHatchBE(BlockPos pos, BlockState state) {
        return new FluidHatchBlockEntity((BlockEntityType<?>)FLUID_HATCH_BE.get(), pos, state);
    }

    private LoconauticsRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
    }
}
