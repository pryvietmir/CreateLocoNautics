package com.lycoris.loconautics;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.client.StationParkingFlagClient;
import com.lycoris.loconautics.client.connected.ConnectedModelLoader;
import com.lycoris.loconautics.client.connected.ConnectedVariantModelLoader;
import com.lycoris.loconautics.client.copycat.CopycatBedLoader;
import com.lycoris.loconautics.client.copycat.CopycatBoxesLoader;
import com.lycoris.loconautics.client.gui.CreateConfigRedirect;
import com.lycoris.loconautics.client.gui.selector.PanelPartScreens;
import com.lycoris.loconautics.client.ponder.BoilerPonderPlugin;
import com.lycoris.loconautics.client.ponder.HosePonderPlugin;
import com.lycoris.loconautics.client.ponder.LoconauticsPonderPlugin;
import com.lycoris.loconautics.client.ponder.SmallEnginePonderPlugin;
import com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler;
import com.lycoris.loconautics.client.scrollmenu.ScrollMenuRenderer;
import com.lycoris.loconautics.common.strand.ClientStrands;
import com.lycoris.loconautics.common.strand.StrandKnotPicker;
import com.lycoris.loconautics.compat.create.patch.RollerAnimator;
import com.lycoris.loconautics.compat.crn.CrnCartDisplays;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlocks;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerClientHandler;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerHUD;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerRenderer;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerScreen;
import com.lycoris.loconautics.content.analogcontroller.WhistleHonks;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleRenderer;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleScreen;
import com.lycoris.loconautics.content.bearingaxle.DrivenBogeyModel;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.boiler.barrel.BarrelCTBehaviour;
import com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlock;
import com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorRenderer;
import com.lycoris.loconautics.content.boiler.barrel.BoilerCTShiftEntry;
import com.lycoris.loconautics.content.boiler.barrel.BoilerCTType;
import com.lycoris.loconautics.content.boiler.firebox.FireboxRenderer;
import com.lycoris.loconautics.content.boiler.firebox.FireboxVisual;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffRenderer;
import com.lycoris.loconautics.content.brakes.pull.PullBrakeHold;
import com.lycoris.loconautics.content.brakes.pull.PullBrakeRenderer;
import com.lycoris.loconautics.content.brakes.pull.PullBrakeVisual;
import com.lycoris.loconautics.content.buffer.BufferBlocks;
import com.lycoris.loconautics.content.buffer.BufferRenderer;
import com.lycoris.loconautics.content.casingpicker.BogeyItem;
import com.lycoris.loconautics.content.casingpicker.CasingPickerClient;
import com.lycoris.loconautics.content.cerisene.CeriseneBlocks;
import com.lycoris.loconautics.content.cerisene.CeriseneFluidType;
import com.lycoris.loconautics.content.cerisene.CeriseneTint;
import com.lycoris.loconautics.content.combustion.SmallEngineBlocks;
import com.lycoris.loconautics.content.combustion.SmallEngineRenderer;
import com.lycoris.loconautics.content.combustion.SmallEngineVisual;
import com.lycoris.loconautics.content.controls.panel.PanelRenderer;
import com.lycoris.loconautics.content.controls.panel.visual.PanelVisual;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlock;
import com.lycoris.loconautics.content.controls.panellever.LeverHoldInteraction;
import com.lycoris.loconautics.content.dashboard.DashboardBlocks;
import com.lycoris.loconautics.content.dashboard.DashboardRenderer;
import com.lycoris.loconautics.content.ghostwheel.GhostWheelBlock;
import com.lycoris.loconautics.content.grab.GrabClientHandler;
import com.lycoris.loconautics.content.grab.GrabCrosshair;
import com.lycoris.loconautics.content.grab.GrabHandRenderer;
import com.lycoris.loconautics.content.hatch.HatchRenderer;
import com.lycoris.loconautics.content.hose.HoseInteractionHandler;
import com.lycoris.loconautics.content.hose.link.HoseConnectorRenderer;
import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntities;
import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntityRenderer;
import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayParticle;
import com.lycoris.loconautics.content.hose.winch.HoseWinchKnotSource;
import com.lycoris.loconautics.content.hose.winch.HoseWinchRenderer;
import com.lycoris.loconautics.content.industrial.IndustrialBlocks;
import com.lycoris.loconautics.content.industrial.IndustrialClientSetup;
import com.lycoris.loconautics.content.industrial.IndustrialTankRenderer;
import com.lycoris.loconautics.content.knuckle.KnuckleRenderer;
import com.lycoris.loconautics.content.relocator.CreativeTrainRelocatorItem;
import com.lycoris.loconautics.content.relocator.SableTrainRelocator;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrandRenderer;
import com.lycoris.loconautics.content.steelcable.SteelCableHover;
import com.lycoris.loconautics.content.steelcable.SteelCablePreview;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandRenderer;
import com.lycoris.loconautics.content.transmission.TransmissionScreen;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingRenderer;
import com.lycoris.loconautics.content.turntablebearing.TurntableBearingVisual;
import com.lycoris.loconautics.content.turntablebearing.TurntablePlateRenderer;
import com.lycoris.loconautics.content.variantpicker.ScrollPickerHandler;
import com.lycoris.loconautics.foundation.debug.DebugLinesClient;
import com.lycoris.loconautics.foundation.debug.TickMeterHud;
import com.lycoris.loconautics.foundation.particle.LateParticles;
import com.lycoris.loconautics.foundation.smoke.SmokeParticle;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerScrollPacket;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.actors.TrainActorControls;
import com.lycoris.loconautics.trainphysics.fx.SableTrainParticles;
import com.lycoris.loconautics.trainphysics.fx.SableTrainSounds;
import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.visual.BogeyTurnTweakCommand;
import com.lycoris.loconautics.trainphysics.visual.BogeyWheelAnimator;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.item.ItemDescription.Modifier;
import com.simibubi.create.foundation.item.render.CustomRenderedItems;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Pre;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.InputEvent.Key;
import net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent;
import net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.LevelEvent.Unload;
import net.neoforged.neoforge.fluids.FluidType;

@Mod(value = "loconautics", dist = Dist.CLIENT)
public final class LoconauticsClient {
    public LoconauticsClient(ModContainer container, IEventBus modBus) {
        container.registerConfig(Type.CLIENT, LoconauticsConfig.CLIENT_SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeyMappings);
        modBus.addListener(this::onRegisterRenderers);
        modBus.addListener(this::onRegisterScreens);
        modBus.addListener(this::onRegisterGuiLayers);
        modBus.addListener(this::onRegisterParticleProviders);
        modBus.addListener(this::onRegisterGeometryLoaders);
        modBus.addListener(this::onRegisterClientExtensions);
        modBus.addListener(this::onRegisterBlockColors);
        modBus.addListener(this::onAddPackFinders);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onKeyInput);
        NeoForge.EVENT_BUS.addListener(this::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(this::onClientDisconnect);
        NeoForge.EVENT_BUS.addListener(this::onClientLevelUnload);
        NeoForge.EVENT_BUS.addListener(this::onUseItem);
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(this::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onCalculatePlayerTurn);
        NeoForge.EVENT_BUS.addListener(CreateConfigRedirect::onScreenInit);
    }

    private void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES && !ModList.get().isLoaded("railways")) {
            event.addPackFinders(
                LoconauticsConstants.id("resourcepacks/casing_tooltip"),
                PackType.CLIENT_RESOURCES,
                Component.literal("Loconautics casing tooltip"),
                PackSource.BUILT_IN,
                true,
                Position.TOP
            );
        }
    }

    private void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet((ParticleType)LoconauticsRegistries.HOSE_WATER_SPRAY.get(), HoseSprayParticle.Provider::new);
        event.registerSpriteSet((ParticleType)LoconauticsRegistries.SMOKE.get(), SmokeParticle.Provider::new);
    }

    private void onRegisterBlockColors(Block event) {
        event.register(
            (state, level, pos, tintIndex) -> CeriseneTint.color(state),
            new net.minecraft.world.level.block.Block[]{(net.minecraft.world.level.block.Block)CeriseneBlocks.CERISENE_BLOCK.get()}
        );
    }

    private void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(CeriseneFluidType.clientExtensions(), new FluidType[]{(FluidType)CeriseneBlocks.CERISENE_TYPE.get()});
        event.registerBlock(
            BarrelConnectorBlock.clientExtensions(),
            new net.minecraft.world.level.block.Block[]{(net.minecraft.world.level.block.Block)BoilerBlocks.BARREL_PIPE_CONNECTOR.get()}
        );
        event.registerBlock(
            GaugeBlock.clientExtensions(),
            new net.minecraft.world.level.block.Block[]{(net.minecraft.world.level.block.Block)BoilerBlocks.LOCOMOTIVE_GAUGE.get()}
        );
        event.registerBlock(
            GhostWheelBlock.clientExtensions(),
            new net.minecraft.world.level.block.Block[]{(net.minecraft.world.level.block.Block)LoconauticsRegistries.GHOST_WHEEL.get()}
        );
        event.registerItem(CreativeTrainRelocatorItem.clientExtensions(), new Item[]{(Item)LoconauticsRegistries.CREATIVE_TRAIN_RELOCATOR.get()});
        event.registerItem(BogeyItem.clientExtensions(), new Item[]{(Item)LoconauticsRegistries.BOGEY.get()});
    }

    private void onRegisterGeometryLoaders(RegisterGeometryLoaders event) {
        event.register(LoconauticsConstants.id("connected"), new ConnectedModelLoader());
        event.register(LoconauticsConstants.id("connected_variants"), new ConnectedVariantModelLoader());
        event.register(LoconauticsConstants.id("copycat_bed"), new CopycatBedLoader());
        event.register(LoconauticsConstants.id("copycat_boxes"), new CopycatBoxesLoader());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        BoilerCTType.register();
        registerBarrelCT((net.minecraft.world.level.block.Block)BoilerBlocks.BOILER_BODY.get(), "boiler_body");
        registerCeriseneCT();
        IndustrialClientSetup.register();

        for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof AbstractBogeyBlock) {
                registerDrivenBogey(block);
            }
        }
    }

    private static void registerDrivenBogey(net.minecraft.world.level.block.Block bogey) {
        ResourceLocation id = RegisteredObjectsHelper.getKeyOrThrow(bogey);
        boolean standard = DrivenBogeyModel.isStandardShape(id);
        CreateClient.MODEL_SWAPPER.getCustomBlockModels().register(id, original -> new DrivenBogeyModel(original, standard));
    }

    private static void registerCeriseneCT() {
        CTSpriteShiftEntry shift = CTSpriteShifter.getCT(
            AllCTTypes.OMNIDIRECTIONAL, LoconauticsConstants.id("block/cerisene/block"), LoconauticsConstants.id("block/cerisene/block_connected")
        );
        CreateClient.MODEL_SWAPPER
            .getCustomBlockModels()
            .register(
                RegisteredObjectsHelper.getKeyOrThrow((net.minecraft.world.level.block.Block)CeriseneBlocks.CERISENE_BLOCK.get()),
                model -> new CTModel(model, new SimpleCTBehaviour(shift))
            );
    }

    private static void registerBarrelCT(net.minecraft.world.level.block.Block block, String folder) {
        String base = "block/boiler/barrel/" + folder + "/barrel_side";
        BoilerCTShiftEntry shift = BoilerCTShiftEntry.get(LoconauticsConstants.id(base), LoconauticsConstants.id(base + "_ct"));
        CreateClient.MODEL_SWAPPER
            .getCustomBlockModels()
            .register(RegisteredObjectsHelper.getKeyOrThrow(block), model -> new CTModel(model, new BarrelCTBehaviour(shift)));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        LeverHoldInteraction.registered();
        PullBrakeHold.registered();
        LoconauticsConstants.LOGGER.info("Loconautics client setup");
        LoconauticsPartialModels.init();
        LoconauticsSpriteShifts.init();
        CustomRenderedItems.register((Item)LoconauticsRegistries.CREATIVE_TRAIN_RELOCATOR.get());
        CustomRenderedItems.register((Item)LoconauticsRegistries.BOGEY.get());
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer((Fluid)CeriseneBlocks.CERISENE.get(), RenderType.translucent()));
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer((Fluid)CeriseneBlocks.CERISENE_FLOWING.get(), RenderType.translucent()));
        PonderIndex.addPlugin(new LoconauticsPonderPlugin());
        PonderIndex.addPlugin(new BoilerPonderPlugin());
        PonderIndex.addPlugin(new HosePonderPlugin());
        PonderIndex.addPlugin(new SmallEnginePonderPlugin());
        SimpleBlockEntityVisualizer.builder(BoilerBlocks.FIREBOX_BE.get()).factory(FireboxVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(BoilerBlocks.LOCOMOTIVE_GAUGE_BE.get()).factory(PanelVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(DashboardBlocks.DASHBOARD_BE.get()).factory(PanelVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SmallEngineBlocks.ENGINE_BE.get()).factory(SmallEngineVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(LoconauticsRegistries.PULL_BRAKE_BE.get()).factory(PullBrakeVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(LoconauticsRegistries.TRANSMISSION_BE.get()).factory(SplitShaftVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(LoconauticsRegistries.BEARING_AXLE_BE.get())
            .factory(
                (context, be, partialTick) -> new OrientedRotatingVisual(
                    context, be, partialTick, Direction.SOUTH, BearingAxleBlock.shaftFace(be.getBlockState()), Models.partial(AllPartialModels.SHAFT_HALF)
                )
            )
            .apply();
        SimpleBlockEntityVisualizer.builder(LoconauticsRegistries.TURNTABLE_BEARING_BE.get()).factory(TurntableBearingVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(LoconauticsRegistries.TURNTABLE_PLATE_BE.get())
            .factory(OrientedRotatingVisual.of(LoconauticsPartialModels.TURNTABLE_SHAFT))
            .apply();
        ItemDescription.useKey((ItemLike)LoconauticsRegistries.STEEL_CABLE.get(), "item.loconautics.steel_cable");
        TooltipModifier.REGISTRY
            .register((Item)LoconauticsRegistries.STEEL_CABLE.get(), new Modifier((Item)LoconauticsRegistries.STEEL_CABLE.get(), Palette.STANDARD_CREATE));
        ItemDescription.useKey((ItemLike)LoconauticsRegistries.KNUCKLE_ITEM.get(), "block.loconautics.knuckle");
        TooltipModifier.REGISTRY
            .register((Item)LoconauticsRegistries.KNUCKLE_ITEM.get(), new Modifier((Item)LoconauticsRegistries.KNUCKLE_ITEM.get(), Palette.STANDARD_CREATE));
        StrandKnotPicker.registerSource(new HoseWinchKnotSource());
        registerTooltip((Item)CeriseneBlocks.RAW_CERISENE.get(), "item.loconautics.raw_cerisene");
        registerTooltip((Item)CeriseneBlocks.CERISENE_BLOCK_ITEM.get(), "block.loconautics.cerisene_block");
        registerTooltip((Item)LoconauticsRegistries.HOSE.get(), "item.loconautics.hose");
        SmallEngineBlocks.ENGINE_ITEMS.forEach((color, item) -> registerTooltip((Item)item.get(), "block.loconautics.small_combustion_engine"));
        AnalogControllerBlocks.CONTROLLER_ITEMS.forEach((color, item) -> registerTooltip((Item)item.get(), "block.loconautics.analog_controller"));
        registerTooltip((Item)LoconauticsRegistries.BEARING_AXLE_ITEM.get(), "block.loconautics.bearing_axle");
        registerTooltip((Item)LoconauticsRegistries.TRANSMISSION_ITEM.get(), "block.loconautics.transmission");
        registerTooltip((Item)BoilerBlocks.LOCOMOTIVE_GAUGE_ITEM.get(), "block.loconautics.locomotive_gauge");
        registerTooltip((Item)BoilerBlocks.SMART_SAFETY_VALVE_ITEM.get(), "block.loconautics.smart_safety_valve");
        DashboardBlocks.DASHBOARD_ITEMS.forEach((color, item) -> registerTooltip((Item)item.get(), "block.loconautics.dashboard"));
        registerTooltip((Item)LoconauticsRegistries.SMOKESTACK_ITEM.get(), "block.loconautics.smokestack");
        registerTooltip((Item)BoilerBlocks.VALVE_ITEM.get(), "item.loconautics.locomotive_valve");
        registerTooltip((Item)BoilerBlocks.GAUGE_BUTTON_ITEM.get(), "item.loconautics.gauge_button");
        registerTooltip((Item)BoilerBlocks.GAUGE_LEVER_ITEM.get(), "item.loconautics.gauge_lever");
        registerTooltip((Item)BoilerBlocks.GAUGE_SWITCH_ITEM.get(), "item.loconautics.gauge_switch");
        registerTooltip((Item)IndustrialBlocks.TANK_ITEM.get(), "block.loconautics.industrial_fluid_tank");
        registerTooltip((Item)IndustrialBlocks.PUMP_ITEM.get(), "block.loconautics.industrial_pump");
    }

    private static void registerTooltip(Item item, String key) {
        ItemDescription.useKey(item, key);
        TooltipModifier.REGISTRY.register(item, new Modifier(item, Palette.STANDARD_CREATE));
    }

    private void onRegisterRenderers(RegisterRenderers event) {
        event.registerBlockEntityRenderer(LoconauticsRegistries.ANALOG_CONTROLLER_BE.get(), AnalogControllerRenderer::new);
        event.registerBlockEntityRenderer(SmallEngineBlocks.ENGINE_BE.get(), SmallEngineRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.PULL_BRAKE_BE.get(), PullBrakeRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.TURNTABLE_BEARING_BE.get(), TurntableBearingRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.TURNTABLE_PLATE_BE.get(), TurntablePlateRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.BEARING_AXLE_BE.get(), BearingAxleRenderer::new);
        event.registerBlockEntityRenderer(BoilerBlocks.STEAM_TAKEOFF_BE.get(), SteamTakeoffRenderer::new);
        event.registerBlockEntityRenderer(BoilerBlocks.BARREL_PIPE_CONNECTOR_BE.get(), BarrelConnectorRenderer::new);
        event.registerBlockEntityRenderer(BoilerBlocks.LOCOMOTIVE_GAUGE_BE.get(), PanelRenderer::new);
        event.registerBlockEntityRenderer(DashboardBlocks.DASHBOARD_BE.get(), DashboardRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.TRANSMISSION_BE.get(), SplitShaftRenderer::new);
        event.registerBlockEntityRenderer(IndustrialBlocks.TANK_BE.get(), IndustrialTankRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.KNUCKLE_BE.get(), KnuckleRenderer::new);
        event.registerBlockEntityRenderer(BufferBlocks.BUFFER_BE.get(), BufferRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.HOSE_CONNECTOR_BE.get(), HoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.HOSE_WINCH_BE.get(), HoseWinchRenderer::new);
        event.registerBlockEntityRenderer(LoconauticsRegistries.FLUID_HATCH_BE.get(), HatchRenderer::new);
        event.registerBlockEntityRenderer(BoilerBlocks.FIREBOX_BE.get(), FireboxRenderer::new);
        event.registerEntityRenderer((EntityType)HoseSprayEntities.HOSE_SPRAY.get(), HoseSprayEntityRenderer::new);
    }

    private void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register((MenuType)LoconauticsRegistries.ANALOG_CONTROLLER_MENU.get(), AnalogControllerScreen::new);
        event.register((MenuType)LoconauticsRegistries.TRANSMISSION_MENU.get(), TransmissionScreen::new);
        event.register((MenuType)LoconauticsRegistries.BEARING_AXLE_MENU.get(), BearingAxleScreen::new);
        event.register((MenuType)LoconauticsRegistries.PANEL_FREQUENCY_MENU.get(), PanelPartScreens::create);
    }

    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, LoconauticsConstants.id("analog_controller_hud"), AnalogControllerHUD.OVERLAY);
        event.registerAbove(VanillaGuiLayers.HOTBAR, LoconauticsConstants.id("scroll_picker"), ScrollMenuRenderer.OVERLAY);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LoconauticsConstants.id("grab_crosshair"), GrabCrosshair.OVERLAY);
        event.registerAboveAll(LoconauticsConstants.id("tick_meter"), TickMeterHud.OVERLAY);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        BogeyTurnTweakCommand.register(event.getDispatcher());
    }

    private void onClientTick(Pre event) {
        if (Minecraft.getInstance().level != null) {
            RailSeams.trackClientLevel(Minecraft.getInstance().level);
        }

        PortalCutsClient.tickReport();
        AnalogControllerClientHandler.tick();
        AnalogControllerHUD.tick();
        SableTrainRelocator.clientTick();
        SableTrainSounds.tickAll();
        WhistleHonks.tick();
        SableTrainParticles.tickAll();
        BogeyWheelAnimator.tickAll();
        RollerAnimator.tickAll();
        StationParkingFlagClient.tick();
        HoseInteractionHandler.tickPreview();
        SteelCablePreview.tickPreview();
        SteelCableHover.tick();
        GrabClientHandler.tick();
        GrabHandRenderer.tick();
        DebugLinesClient.tick();
        ScrollMenuHandler.tick();
    }

    private void onUseItem(InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && SteelCableHover.onUse()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        } else {
            SableTrainRelocator.onInteract(event);
            HoseInteractionHandler.onInteract(event);
            GrabClientHandler.onInteract(event);
        }
    }

    private void onRenderGuiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.TAB_LIST) && ScrollMenuHandler.isOpen()) {
            event.setCanceled(true);
        }

        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && GrabCrosshair.replacesCrosshair()) {
            event.setCanceled(true);
        }
    }

    private void onCalculatePlayerTurn(CalculatePlayerTurnEvent event) {
        GrabClientHandler.onCalculateTurn(event);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(GrabClientHandler.ROTATE_MODE);
        event.register(ScrollPickerHandler.OPEN_KEY);
        event.register(CasingPickerClient.OPEN_KEY);
    }

    private void onKeyInput(Key event) {
        if (ScrollPickerHandler.OPEN_KEY.matches(event.getKey(), event.getScanCode()) && event.getAction() == 1 && Minecraft.getInstance().screen == null) {
            ScrollPickerHandler.tryOpen();
        }

        if (CasingPickerClient.OPEN_KEY.matches(event.getKey(), event.getScanCode()) && event.getAction() == 1 && Minecraft.getInstance().screen == null) {
            CasingPickerClient.tryOpen();
        }

        if (AnalogControllerClientHandler.isControlling()) {
            if (event.getKey() == 256 && event.getAction() == 1) {
                AnalogControllerClientHandler.stopControllingClient();
                Minecraft.getInstance().setScreen(null);
            }
        }
    }

    private void onMouseScroll(MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && ScrollMenuHandler.isOpen() && ScrollMenuHandler.onScroll(event.getScrollDeltaY() > 0.0 ? -1 : 1)) {
            event.setCanceled(true);
        } else if (mc.screen == null && GrabClientHandler.onScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        } else if (AnalogControllerClientHandler.isControlling()) {
            if (Minecraft.getInstance().screen == null) {
                int delta = event.getScrollDeltaY() > 0.0 ? 1 : -1;
                CatnipServices.NETWORK.sendToServer(new AnalogControllerScrollPacket(delta, AnalogControllerClientHandler.getMountedPos()));
                event.setCanceled(true);
            }
        }
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == Stage.AFTER_WEATHER) {
            BufferSource lineBuffers = Minecraft.getInstance().renderBuffers().bufferSource();
            DebugLinesClient.renderAll(event.getPoseStack(), lineBuffers, event.getCamera().getPosition());
            lineBuffers.endBatch();
            LateParticles.renderAll(event.getCamera(), event.getModelViewMatrix(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        } else if (event.getStage() == Stage.AFTER_ENTITIES) {
            BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            SteelCableStrandRenderer.renderAll(
                event.getPoseStack(), buffers, event.getCamera().getPosition(), event.getPartialTick().getGameTimeDeltaPartialTick(false)
            );
            ScrewLinkStrandRenderer.renderAll(
                event.getPoseStack(), buffers, event.getCamera().getPosition(), event.getPartialTick().getGameTimeDeltaPartialTick(false)
            );
            buffers.endBatch();
        }
    }

    private void onClientLevelUnload(Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            PortalCutsClient.onLevelUnload(level);
            LateParticles.clear();
            AnalogControllerClientHandler.clear();
            RailSeams.untrackClientLevel(level);
        }
    }

    private void onClientDisconnect(LoggingOut event) {
        ClientStrands.clearAll();
        SableTrainClientRegistry.clear();
        CrnCartDisplays.clear();
        TrainActorControls.clearClient();
        SableTrainSounds.stopAll();
        WhistleHonks.clear();
        StationParkingFlagClient.clear();
        PortalCutsClient.clear();
        DebugLinesClient.clear();
        ScrollMenuHandler.forceClose();
        AnalogControllerClientHandler.clear();
        RailSeams.clearClientLevels();
    }
}
