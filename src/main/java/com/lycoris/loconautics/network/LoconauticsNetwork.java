package com.lycoris.loconautics.network;

import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerDismountPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerInputPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerMountPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerScrollPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerStationPromptPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabDragPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabStopPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabUndockPacket;
import com.lycoris.loconautics.network.packets.attachment.HoseAttachPacket;
import com.lycoris.loconautics.network.packets.attachment.HoseFlowPacket;
import com.lycoris.loconautics.network.packets.attachment.HoseLinkPacket;
import com.lycoris.loconautics.network.packets.attachment.HoseSprayColorSyncPacket;
import com.lycoris.loconautics.network.packets.attachment.SteelCableBreakPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandHookGrabPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandKnotGrabPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import com.lycoris.loconautics.network.packets.casingpicker.CasingSwapPacket;
import com.lycoris.loconautics.network.packets.controls.LeverSetPacket;
import com.lycoris.loconautics.network.packets.controls.LocomotiveGaugeSelectPacket;
import com.lycoris.loconautics.network.packets.controls.ValveOpenPacket;
import com.lycoris.loconautics.network.packets.controls.ValveTurnPacket;
import com.lycoris.loconautics.network.packets.controls.selector.ButtonSelectorApplyPacket;
import com.lycoris.loconautics.network.packets.controls.selector.LeverSelectorApplyPacket;
import com.lycoris.loconautics.network.packets.controls.selector.PanelInvertPacket;
import com.lycoris.loconautics.network.packets.controls.selector.PanelLabelPacket;
import com.lycoris.loconautics.network.packets.controls.selector.SwitchSelectorApplyPacket;
import com.lycoris.loconautics.network.packets.controls.selector.ValveSelectorApplyPacket;
import com.lycoris.loconautics.network.packets.debug.DebugLinePacket;
import com.lycoris.loconautics.network.packets.pullbrake.PullBrakeSetPacket;
import com.lycoris.loconautics.network.packets.schedule.BlazeConductorHatPacket;
import com.lycoris.loconautics.network.packets.schedule.ConductorHatPacket;
import com.lycoris.loconautics.network.packets.schedule.MapColorRequestPacket;
import com.lycoris.loconautics.network.packets.schedule.MapColorResponsePacket;
import com.lycoris.loconautics.network.packets.train.AssembleSableTrainPacket;
import com.lycoris.loconautics.network.packets.train.DisassembleSableTrainPacket;
import com.lycoris.loconautics.network.packets.train.PlaceStoredTrainPacket;
import com.lycoris.loconautics.network.packets.train.PortalCutPacket;
import com.lycoris.loconautics.network.packets.train.RelocatePreviewPacket;
import com.lycoris.loconautics.network.packets.train.RelocatePreviewResultPacket;
import com.lycoris.loconautics.network.packets.train.SableTrainRelocatePacket;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.network.packets.train.StationParkedSyncPacket;
import com.lycoris.loconautics.network.packets.train.StationSetTrainColorPacket;
import com.lycoris.loconautics.network.packets.train.StationSetTrainNamePacket;
import com.lycoris.loconautics.network.packets.train.SubLevelFrameSyncPacket;
import com.lycoris.loconautics.network.packets.train.SuspensionLeanPacket;
import com.lycoris.loconautics.network.packets.train.TrainHornPacket;
import com.lycoris.loconautics.network.packets.turntablebearing.TurntableConfigurePacket;
import com.lycoris.loconautics.network.packets.variantpicker.BlockVariantApplyPacket;
import com.lycoris.loconautics.network.packets.variantpicker.VariantFamilySyncPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "loconautics")
public final class LoconauticsNetwork {
    private LoconauticsNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(String.valueOf(80));
        r.playToServer(BlockVariantApplyPacket.TYPE, BlockVariantApplyPacket.STREAM_CODEC, BlockVariantApplyPacket::handle);
        r.playToServer(CasingSwapPacket.TYPE, CasingSwapPacket.STREAM_CODEC, CasingSwapPacket::handle);
        r.playToClient(VariantFamilySyncPacket.TYPE, VariantFamilySyncPacket.STREAM_CODEC, VariantFamilySyncPacket::handle);
        r.playToServer(LocomotiveGaugeSelectPacket.TYPE, LocomotiveGaugeSelectPacket.STREAM_CODEC, LocomotiveGaugeSelectPacket::handle);
        r.playToServer(ValveTurnPacket.TYPE, ValveTurnPacket.STREAM_CODEC, ValveTurnPacket::handle);
        r.playToClient(ValveOpenPacket.TYPE, ValveOpenPacket.STREAM_CODEC, ValveOpenPacket::handle);
        r.playToServer(ValveSelectorApplyPacket.TYPE, ValveSelectorApplyPacket.STREAM_CODEC, ValveSelectorApplyPacket::handle);
        r.playToServer(ButtonSelectorApplyPacket.TYPE, ButtonSelectorApplyPacket.STREAM_CODEC, ButtonSelectorApplyPacket::handle);
        r.playToServer(LeverSelectorApplyPacket.TYPE, LeverSelectorApplyPacket.STREAM_CODEC, LeverSelectorApplyPacket::handle);
        r.playToServer(SwitchSelectorApplyPacket.TYPE, SwitchSelectorApplyPacket.STREAM_CODEC, SwitchSelectorApplyPacket::handle);
        r.playToServer(PanelInvertPacket.TYPE, PanelInvertPacket.STREAM_CODEC, PanelInvertPacket::handle);
        r.playToServer(PanelLabelPacket.TYPE, PanelLabelPacket.STREAM_CODEC, PanelLabelPacket::handle);
        r.playToServer(TurntableConfigurePacket.TYPE, TurntableConfigurePacket.STREAM_CODEC, TurntableConfigurePacket::handle);
        r.playToServer(LeverSetPacket.TYPE, LeverSetPacket.STREAM_CODEC, LeverSetPacket::handle);
        r.playToServer(PullBrakeSetPacket.TYPE, PullBrakeSetPacket.STREAM_CODEC, PullBrakeSetPacket::handle);
        r.playToServer(AnalogControllerInputPacket.TYPE, AnalogControllerInputPacket.STREAM_CODEC, AnalogControllerInputPacket::handle);
        r.playToServer(AnalogControllerScrollPacket.TYPE, AnalogControllerScrollPacket.STREAM_CODEC, AnalogControllerScrollPacket::handle);
        r.playToServer(AnalogControllerDismountPacket.TYPE, AnalogControllerDismountPacket.STREAM_CODEC, AnalogControllerDismountPacket::handle);
        r.playToClient(AnalogControllerMountPacket.TYPE, AnalogControllerMountPacket.STREAM_CODEC, AnalogControllerMountPacket::handle);
        r.playToClient(AnalogControllerStationPromptPacket.TYPE, AnalogControllerStationPromptPacket.STREAM_CODEC, AnalogControllerStationPromptPacket::handle);
        r.playToServer(AssembleSableTrainPacket.TYPE, AssembleSableTrainPacket.STREAM_CODEC, AssembleSableTrainPacket::handle);
        r.playToServer(DisassembleSableTrainPacket.TYPE, DisassembleSableTrainPacket.STREAM_CODEC, DisassembleSableTrainPacket::handle);
        r.playToServer(SableTrainRelocatePacket.TYPE, SableTrainRelocatePacket.STREAM_CODEC, SableTrainRelocatePacket::handle);
        r.playToServer(PlaceStoredTrainPacket.TYPE, PlaceStoredTrainPacket.STREAM_CODEC, PlaceStoredTrainPacket::handle);
        r.playToServer(RelocatePreviewPacket.TYPE, RelocatePreviewPacket.STREAM_CODEC, RelocatePreviewPacket::handle);
        r.playToClient(RelocatePreviewResultPacket.TYPE, RelocatePreviewResultPacket.STREAM_CODEC, RelocatePreviewResultPacket::handle);
        r.playToServer(StationSetTrainNamePacket.TYPE, StationSetTrainNamePacket.STREAM_CODEC, StationSetTrainNamePacket::handle);
        r.playToServer(StationSetTrainColorPacket.TYPE, StationSetTrainColorPacket.STREAM_CODEC, StationSetTrainColorPacket::handle);
        r.playToClient(SableTrainSyncPacket.TYPE, SableTrainSyncPacket.STREAM_CODEC, SableTrainSyncPacket::handle);
        r.playToClient(SuspensionLeanPacket.TYPE, SuspensionLeanPacket.STREAM_CODEC, SuspensionLeanPacket::handle);
        r.playToClient(StationParkedSyncPacket.TYPE, StationParkedSyncPacket.STREAM_CODEC, StationParkedSyncPacket::handle);
        r.playToClient(PortalCutPacket.TYPE, PortalCutPacket.STREAM_CODEC, PortalCutPacket::handle);
        r.playToClient(TrainHornPacket.TYPE, TrainHornPacket.STREAM_CODEC, TrainHornPacket::handle);
        r.playToClient(DebugLinePacket.TYPE, DebugLinePacket.STREAM_CODEC, DebugLinePacket::handle);
        r.playToServer(HoseAttachPacket.TYPE, HoseAttachPacket.STREAM_CODEC, HoseAttachPacket::handle);
        r.playToServer(HoseLinkPacket.TYPE, HoseLinkPacket.STREAM_CODEC, HoseLinkPacket::handle);
        r.playToServer(GrabDragPacket.TYPE, GrabDragPacket.STREAM_CODEC, GrabDragPacket::handle);
        r.playToServer(GrabStopPacket.TYPE, GrabStopPacket.STREAM_CODEC, GrabStopPacket::handle);
        r.playToServer(GrabUndockPacket.TYPE, GrabUndockPacket.STREAM_CODEC, GrabUndockPacket::handle);
        r.playToClient(StrandPointsPacket.TYPE, StrandPointsPacket.STREAM_CODEC, StrandPointsPacket::handle);
        r.playToClient(SubLevelFrameSyncPacket.TYPE, SubLevelFrameSyncPacket.STREAM_CODEC, SubLevelFrameSyncPacket::handle);
        r.playToClient(HoseFlowPacket.TYPE, HoseFlowPacket.STREAM_CODEC, HoseFlowPacket::handle);
        r.playToServer(StrandHookGrabPacket.TYPE, StrandHookGrabPacket.STREAM_CODEC, StrandHookGrabPacket::handle);
        r.playToServer(StrandKnotGrabPacket.TYPE, StrandKnotGrabPacket.STREAM_CODEC, StrandKnotGrabPacket::handle);
        r.playToServer(SteelCableBreakPacket.TYPE, SteelCableBreakPacket.STREAM_CODEC, SteelCableBreakPacket::handle);
        r.playToClient(HoseSprayColorSyncPacket.TYPE, HoseSprayColorSyncPacket.STREAM_CODEC, HoseSprayColorSyncPacket::handle);
        r.playToServer(MapColorRequestPacket.TYPE, MapColorRequestPacket.STREAM_CODEC, MapColorRequestPacket::handle);
        r.playToClient(MapColorResponsePacket.TYPE, MapColorResponsePacket.STREAM_CODEC, MapColorResponsePacket::handle);
        r.playToClient(ConductorHatPacket.TYPE, ConductorHatPacket.STREAM_CODEC, ConductorHatPacket::handle);
        r.playToClient(BlazeConductorHatPacket.TYPE, BlazeConductorHatPacket.STREAM_CODEC, BlazeConductorHatPacket::handle);
    }

    public static void sendMount(ServerPlayer player, boolean mounted, BlockPos pos) {
        PacketDistributor.sendToPlayer(player, new AnalogControllerMountPacket(mounted, pos), new CustomPacketPayload[0]);
    }

    public static void sendStationPrompt(ServerPlayer player, Component text, boolean shadow) {
        PacketDistributor.sendToPlayer(player, new AnalogControllerStationPromptPacket(text, shadow), new CustomPacketPayload[0]);
    }

    public static void sendDismount(BlockPos pos) {
        CatnipServices.NETWORK.sendToServer(new AnalogControllerDismountPacket(pos));
    }
}
