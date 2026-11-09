package com.lycoris.loconautics.mixin.client.gui;

import com.lycoris.loconautics.client.gui.SableButtonIcon;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.mixin.client.station.AbstractStationScreenAccessor;
import com.lycoris.loconautics.network.packets.train.AssembleSableTrainPacket;
import com.simibubi.create.content.trains.station.AssemblyScreen;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.StationScreen;
import com.simibubi.create.content.trains.station.WideIconButton;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.util.List;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AssemblyScreen.class, remap = false)
public abstract class AssemblyScreenMixin extends Screen {
    @Unique
    private static final int SABLE_W = 26;
    @Unique
    private static final int QUIT_W = 18;
    @Unique
    private static final int ASSEMBLE_W = 26;
    @Unique
    private static final int GAP = 3;
    @Unique
    private static final int ROW_OFFSET = -8;
    @Shadow
    private IconButton quitAssembly;
    @Shadow
    private IconButton toggleAssemblyButton;
    @Unique
    private WideIconButton loconautics$sableButton;
    @Unique
    private boolean loconautics$sawAssembling;

    protected AssemblyScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void loconautics$addSableTrainButton(CallbackInfo ci) {
        int bgWidth = AllGuiTextures.STATION_ASSEMBLING.getWidth();
        int bgHeight = AllGuiTextures.STATION_ASSEMBLING.getHeight();
        int x = (this.width - bgWidth) / 2;
        int by = (this.height - bgHeight) / 2 + bgHeight - 24;
        StationBlockEntity be = ((AbstractStationScreenAccessor)(Object)this).loconautics$getBlockEntity();
        int rowWidth = 76;
        int rowStart = x + (bgWidth - rowWidth) / 2 + -8;
        int sableX = rowStart;
        int quitX = sableX + 26 + 3;
        int assembleX = quitX + 18 + 3;
        this.loconautics$sableButton = new WideIconButton(sableX, by, SableButtonIcon.INSTANCE);
        this.loconautics$sableButton.setToolTip(Component.literal("Assemble as Sable Train"));
        this.loconautics$sableButton.withCallback(() -> CatnipServices.NETWORK.sendToServer(new AssembleSableTrainPacket(be.getBlockPos())));
        this.addRenderableWidget(this.loconautics$sableButton);
        this.quitAssembly.setX(quitX);
        this.toggleAssemblyButton.setX(assembleX);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void loconautics$switchToIdleWhenAssembled(CallbackInfo ci) {
        if (this.minecraft != null) {
            StationBlockEntity be = ((AbstractStationScreenAccessor)(Object)this).loconautics$getBlockEntity();
            GlobalStation station = ((AbstractStationScreenAccessor)(Object)this).loconautics$getStation();
            if (station != null) {
                if (be.isAssembling()) {
                    this.loconautics$sawAssembling = true;
                } else if (this.loconautics$sawAssembling) {
                    this.minecraft.setScreen(new StationScreen(be, station));
                }

                boolean canAssemble = this.toggleAssemblyButton.active;
                loconautics$gate(this.loconautics$sableButton, canAssemble, (Boolean)LoconauticsConfig.ENABLE_SABLE_TRAINS.get());
                loconautics$gate(this.toggleAssemblyButton, canAssemble, (Boolean)LoconauticsConfig.ENABLE_CREATE_TRAINS.get());
            }
        }
    }

    @Unique
    private static void loconautics$gate(IconButton button, boolean canAssemble, boolean enabled) {
        button.active = canAssemble && enabled;
        if (!enabled) {
            List<Component> tooltip = button.getToolTip();
            if (tooltip.size() <= 1) {
                tooltip.add(Component.translatable("loconautics.station.disabled_by_config").withStyle(ChatFormatting.RED));
            }
        }
    }
}
