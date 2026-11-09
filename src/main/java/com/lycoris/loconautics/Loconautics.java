package com.lycoris.loconautics;

import com.lycoris.loconautics.common.ConnectorDetachers;
import com.lycoris.loconautics.common.railway.PointOverrides;
import com.lycoris.loconautics.common.railway.SignalOverrides;
import com.lycoris.loconautics.common.strand.RemovedSubLevels;
import com.lycoris.loconautics.common.strand.RopeManager;
import com.lycoris.loconautics.common.strand.StrandEnd;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.common.strand.StrandTrackingSystem;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.compat.cbc.CbcRecoilHandler;
import com.lycoris.loconautics.compat.computercraft.LoconauticsCCPlugin;
import com.lycoris.loconautics.compat.create.CreateStickerExtension;
import com.lycoris.loconautics.compat.create.ManeuverEditorInstruction;
import com.lycoris.loconautics.compat.create.patch.PloughWorldActor;
import com.lycoris.loconautics.compat.create.patch.RollerWorldActor;
import com.lycoris.loconautics.compat.simulated.SpringTwistLinks;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.bearingaxle.BogeyShaftPlacement;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.boiler.BoilerBodyInlet;
import com.lycoris.loconautics.content.boiler.BoilerContributions;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.content.buffer.BufferBlocks;
import com.lycoris.loconautics.content.buffer.BufferContactManager;
import com.lycoris.loconautics.content.cerisene.CeriseneBlocks;
import com.lycoris.loconautics.content.combustion.SmallEngineBlocks;
import com.lycoris.loconautics.content.conductorshat.ConductorsHats;
import com.lycoris.loconautics.content.controls.panel.PanelDriveBus;
import com.lycoris.loconautics.content.dashboard.DashboardBlocks;
import com.lycoris.loconautics.content.hatch.HullsteelSocketBridge;
import com.lycoris.loconautics.content.hose.HoseStrandRemap;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.content.hose.spray.HoseSprayLoader;
import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntities;
import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import com.lycoris.loconautics.content.hose.spray.interaction.VanillaHoseSprayInteractions;
import com.lycoris.loconautics.content.industrial.IndustrialBlocks;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.content.knuckle.KnuckleConnectionManager;
import com.lycoris.loconautics.content.knuckle.KnuckleDetacher;
import com.lycoris.loconautics.content.knuckle.KnuckleJoint;
import com.lycoris.loconautics.content.locomotiveready.LocomotiveReadyWorld;
import com.lycoris.loconautics.content.schedule.map.ChunkColorSamplerHandler;
import com.lycoris.loconautics.content.schedule.map.MapColorStorage;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrand;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrandManager;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrandRemap;
import com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRopeManager;
import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandRemap;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRope;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.lycoris.loconautics.content.turntablebearing.TurntableCogPlacement;
import com.lycoris.loconautics.content.variantpicker.data.VariantFamilyLoader;
import com.lycoris.loconautics.network.packets.attachment.HoseSprayColorSyncPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import com.lycoris.loconautics.network.packets.variantpicker.VariantFamilySyncPacket;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.registry.LoconauticsSounds;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.coupling.BlockEntityCouplingProvider;
import com.lycoris.loconautics.trainphysics.coupling.RopeCouplingProvider;
import com.lycoris.loconautics.trainphysics.coupling.ScrewLinkCouplingProvider;
import com.lycoris.loconautics.trainphysics.coupling.SteelCableCouplingProvider;
import com.lycoris.loconautics.trainphysics.debug.RailDebug;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.trainphysics.drive.CarriageCollisions;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.spawn.ArticulationLinks;
import com.lycoris.loconautics.trainphysics.spawn.RerailApproach;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.state.StationChunkTickets;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.trainphysics.visual.pivot.BogeyPivotManager;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.contraptions.chassis.StickerBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

@Mod("loconautics")
public final class Loconautics {
    public Loconautics(IEventBus modEventBus, ModContainer modContainer) {
        LoconauticsRegistries.register(modEventBus);
        BoilerBlocks.register(modEventBus);
        CeriseneBlocks.register(modEventBus);
        HoseSprayEntities.register(modEventBus);
        LocomotiveReadyWorld.register(modEventBus);
        IndustrialBlocks.init();
        DashboardBlocks.init();
        BufferBlocks.init();
        SmallEngineBlocks.init();
        ConductorsHats.init();
        LoconauticsSounds.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(StationChunkTickets::register);
        modContainer.registerConfig(Type.SERVER, LoconauticsConfig.SERVER_SPEC);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        NeoForge.EVENT_BUS.register(new ChunkColorSamplerHandler());
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            FluidHandler.BLOCK,
            LoconauticsRegistries.HOSE_WINCH_BE.get(),
            (be, side) -> side != null && side != be.fluidInputSide() ? null : be.getHoseFluidHandler()
        );
        event.registerBlockEntity(
            FluidHandler.BLOCK,
            LoconauticsRegistries.HOSE_CONNECTOR_BE.get(),
            (be, side) -> side != null && side != be.plateSide() ? null : be.getLinkHandler()
        );
        event.registerBlockEntity(FluidHandler.BLOCK, BoilerBlocks.BARREL_PIPE_CONNECTOR_BE.get(), (be, side) -> be.getFluidHandler());

        for (Block block : BuiltInRegistries.BLOCK) {
            event.registerBlock(
                FluidHandler.BLOCK,
                (level, pos, state, be, side) -> state.is(BoilerTags.BOILER_BODY) ? new BoilerBodyInlet(level, pos) : null,
                new Block[]{block}
            );
        }

        event.registerBlockEntity(FluidHandler.BLOCK, SmallEngineBlocks.ENGINE_BE.get(), (be, side) -> be.fluidHandler(side));
        event.registerBlockEntity(
            ItemHandler.BLOCK, BoilerBlocks.FIREBOX_STOCKER_BE.get(), (be, side) -> be.isPrimary() ? be.inputHandler() : null
        );
        if (ModList.get().isLoaded("hullsteel")) {
            HullsteelSocketBridge.registerCapabilities(event);
        }

        if (ModList.get().isLoaded("computercraft")) {
            LoconauticsCCPlugin.onRegisterCapabilities(event);
        }
    }

    private static void onSubLevelContainerReady(Level level, SubLevelContainer container) {
        if (container instanceof ServerSubLevelContainer serverContainer) {
            serverContainer.trackingSystem()
                .addTrackingPlugin(new StrandTrackingSystem<>(serverContainer.getLevel(), SteelCableRope::kind, SteelCableRopeManager::get));
            KnuckleConnectionManager.watch(serverContainer);
            BufferContactManager.watch(serverContainer);
            RemovedSubLevels.watch(serverContainer);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LoconauticsConstants.LOGGER.info("Loconautics common setup");
        BogeyShaftPlacement.init();
        if (ModList.get().isLoaded("hullsteel")) {
            HullsteelSocketBridge.install();
        }

        if (ModList.get().isLoaded("computercraft")) {
            LoconauticsCCPlugin.install();
        }

        ForceTrace.register();
        StrandRemap.register(SteelCableStrandRemap.INSTANCE);
        StrandRemap.register(HoseStrandRemap.INSTANCE);
        StrandRemap.register(ScrewLinkStrandRemap.INSTANCE);
        event.enqueueWork(TurntableCogPlacement::register);
        event.enqueueWork(ManeuverEditorInstruction::register);
        SableTrainDriver.register();
        BogeyPivotManager.register();
        RopeManager.register(HoseRopeManager::get);
        RopeManager.register(SteelCableRopeManager::get);
        RopeManager.register(ScrewLinkRopeManager::get);
        SableEventPlatform.INSTANCE.onPhysicsTick(HoseRopeManager::onPhysicsTick);
        SableEventPlatform.INSTANCE.onPhysicsTick(SteelCableRopeManager::onPhysicsTick);
        SableEventPlatform.INSTANCE.onPhysicsTick(ScrewLinkRopeManager::onPhysicsTick);
        SableEventPlatform.INSTANCE.onPhysicsTick(RollerWorldActor::onPhysicsTick);
        SableEventPlatform.INSTANCE.onPhysicsTick(PloughWorldActor::onPhysicsTick);
        SableEventPlatform.INSTANCE.onSubLevelContainerReady(Loconautics::onSubLevelContainerReady);
        SubLevelSplitWatcher.registerSplitListener();
        HoseSprayInteractions.setBuiltin(VanillaHoseSprayInteractions::register);
        HoseSprayInteractions.rebuild(() -> {});
        TrainConsist.registerProvider(new RopeCouplingProvider());
        TrainConsist.registerProvider(
            new BlockEntityCouplingProvider<>(SpringBlockEntity.class, SpringBlockEntity::getPartnerSubLevelID, spring -> true).twist(SpringTwistLinks::link)
        );
        TrainConsist.registerProvider(
            new BlockEntityCouplingProvider<>(KnuckleBlockEntity.class, KnuckleBlockEntity::getPartnerSubLevelId, KnuckleBlockEntity::isConnected)
                .twist((own, knuckle) -> knuckle.twistLink(own))
        );
        TrainConsist.registerProvider(
            new BlockEntityCouplingProvider<>(
                    DockingConnectorBlockEntity.class, connector -> connector.otherConnectorSubLevelId, DockingConnectorBlockEntity::hasOtherConnector
                )
                .welded(DockingConnectorBlockEntity::isLocked)
        );
        TrainConsist.registerProvider(
            new BlockEntityCouplingProvider<>(
                    StickerBlockEntity.class, sticker -> ((CreateStickerExtension)sticker).loconautics$getPartnerSubLevelId(), sticker -> true
                )
                .welded(sticker -> true)
        );
        TrainConsist.registerProvider(new SteelCableCouplingProvider());
        TrainConsist.registerProvider(new ScrewLinkCouplingProvider());
        ArticulationLinks.registerProviders();
        ConnectorDetachers.register(new KnuckleDetacher());
        if (ModList.get().isLoaded("createbigcannons")) {
            NeoForge.EVENT_BUS.addListener(CbcRecoilHandler::onEntityJoinLevel);
        }
    }

    private void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (SteelCableStrand strand : SteelCableStrandManager.get(player.getServer()).all()) {
                sendStrandOnLogin(
                    player, strand.type.kind(), strand.id, strand.points, new StrandEnd(strand.subA, strand.localA), new StrandEnd(strand.subB, strand.localB)
                );
            }

            for (ScrewLinkStrand strand : ScrewLinkStrandManager.get(player.getServer()).all()) {
                sendStrandOnLogin(
                    player,
                    StrandKind.SCREW_LINK,
                    strand.id,
                    strand.points,
                    new StrandEnd(strand.subA, strand.localA),
                    strand.localB == null ? null : new StrandEnd(strand.subB, strand.localB)
                );
            }
        }
    }

    private static void sendStrandOnLogin(
        ServerPlayer player, StrandKind kind, UUID id, @Nullable List<Vector3d> points, StrandEnd start, @Nullable StrandEnd end
    ) {
        if (points != null && points.size() >= 2) {
            PacketDistributor.sendToPlayer(player, new StrandPointsPacket(kind, id, 0, true, points, start, end), new CustomPacketPayload[0]);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        this.runStopHook("PanelDriveBus", PanelDriveBus::clear);
        this.runStopHook("MapColorStorage", MapColorStorage::closeAll);
        this.runStopHook("ForceTrace", ForceTrace::reset);
        this.runStopHook("RailDebug", RailDebug::clearSelections);
        this.runStopHook("RerailApproach", RerailApproach::clearAll);
        this.runStopHook("TurntableRegistry", TurntableRegistry::clear);
        this.runStopHook("SignalOverrides", SignalOverrides::clear);
        this.runStopHook("PointOverrides", PointOverrides::clearAll);
        this.runStopHook("GhostWheelBodies", GhostWheelBodies::clear);
        this.runStopHook("KnuckleJoint", KnuckleJoint::clear);
        this.runStopHook("SubLevelContacts", SubLevelContacts::clear);
        this.runStopHook("BogeyPivotManager", BogeyPivotManager::clear);
        this.runStopHook("CarriageCollisions", CarriageCollisions::clear);
        this.runStopHook("CreateTrainShadow", CreateTrainShadow::clear);
        this.runStopHook("SableTrainDriver", SableTrainDriver::clearAll);
        this.runStopHook("TrainResidency", TrainResidency::clear);
    }

    private void runStopHook(String name, Runnable hook) {
        try {
            hook.run();
        } catch (Throwable t) {
            LoconauticsConstants.LOGGER.error("[shutdown] {} failed to clear, its static state may leak into the next server", name, t);
        }
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new HoseSprayLoader());
        event.addListener(new BoilerContributions());
        event.addListener(new VariantFamilyLoader());
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        HoseSprayColorSyncPacket sprayColors = new HoseSprayColorSyncPacket(HoseSprayLoader.loadedColors());
        VariantFamilySyncPacket families = new VariantFamilySyncPacket(VariantFamilyLoader.loaded());
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), sprayColors, new CustomPacketPayload[0]);
            PacketDistributor.sendToPlayer(event.getPlayer(), families, new CustomPacketPayload[0]);
        } else {
            PacketDistributor.sendToAllPlayers(sprayColors, new CustomPacketPayload[0]);
            PacketDistributor.sendToAllPlayers(families, new CustomPacketPayload[0]);
        }
    }
}
