package com.lycoris.loconautics.mixin.client.station;

import com.lycoris.loconautics.network.packets.train.DisassembleSableTrainPacket;
import com.lycoris.loconautics.network.packets.train.StationSetTrainColorPacket;
import com.lycoris.loconautics.network.packets.train.StationSetTrainNamePacket;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainIconType;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.StationScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.List;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StationScreen.class, remap = false)
public abstract class StationScreenMixin extends Screen {
    @Shadow
    private IconButton disassembleTrainButton;
    @Shadow
    private IconButton newTrainButton;
    @Shadow
    private IconButton dropScheduleButton;
    @Shadow
    private EditBox trainNameBox;
    @Shadow
    private ScrollInput colorTypeScroll;
    @Shadow
    private int messedWithColors;
    @Unique
    private boolean loconautics$forced;
    @Unique
    private ScrollInput loconautics$iconScroll;
    @Unique
    private List<ResourceLocation> loconautics$iconTypes;
    @Unique
    private LerpedFloat loconautics$trainPosition;
    @Unique
    private int loconautics$leaving;
    @Unique
    private boolean loconautics$hadTrain;
    @Unique
    private boolean loconautics$seeded;
    @Unique
    private boolean loconautics$sawEmpty;
    @Unique
    private int loconautics$absent;
    @Unique
    private float loconautics$lastApproach = Float.MAX_VALUE;
    @Unique
    private int loconautics$disassembling;
    @Unique
    private static final int loconautics$LOST_GRACE = 5;
    @Unique
    private static final float loconautics$APPROACH_RANGE = 30.0F;
    @Unique
    private int[] loconautics$spans = new int[0];

    private StationScreenMixin() {
        super(Component.empty());
    }

    @Unique
    private StationBlockEntity loconautics$be() {
        return ((AbstractStationScreenAccessor)(Object)this).loconautics$getBlockEntity();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void loconautics$routeColorScroll(CallbackInfo ci) {
        int bgWidth = AllGuiTextures.STATION.getWidth();
        int x = (this.width - bgWidth) / 2;
        int y = (this.height - AllGuiTextures.STATION.getHeight()) / 2;
        this.loconautics$trainPosition = LerpedFloat.linear().startWithValue(0.0);
        this.loconautics$leaving = 0;
        this.loconautics$seeded = false;
        this.loconautics$sawEmpty = false;
        this.loconautics$absent = 0;
        this.loconautics$lastApproach = Float.MAX_VALUE;
        this.loconautics$iconTypes = TrainIconType.REGISTRY.keySet().stream().toList();
        this.loconautics$iconScroll = new ScrollInput(x + 4, y + 17, 162, 14).titled(CreateLang.translateDirect("station.icon_type", new Object[0]));
        this.loconautics$iconScroll.withRange(0, this.loconautics$iconTypes.size());
        this.loconautics$iconScroll.withStepFunction(ctx -> -(Integer)(Object)this.loconautics$iconScroll.standardStep().apply(ctx));
        this.loconautics$iconScroll.calling(index -> this.loconautics$sendStyle());
        this.loconautics$iconScroll.active = false;
        this.loconautics$iconScroll.visible = false;
        this.addRenderableWidget(this.loconautics$iconScroll);
        this.colorTypeScroll.calling(index -> {
            StationBlockEntity be = this.loconautics$be();
            if (SableTrainClientRegistry.isStationParked(be.getBlockPos())) {
                this.loconautics$sendStyle();
            } else {
                Train train = ((AbstractStationScreenAccessor)(Object)this).loconautics$getDisplayedTrain().get();
                if (train != null) {
                    train.mapColorIndex = index;
                    this.messedWithColors = 10;
                }
            }
        });
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void loconautics$showSableDisassemble(CallbackInfo ci) {
        StationBlockEntity be = this.loconautics$be();
        BlockPos pos = be.getBlockPos();
        boolean hasTrain = SableTrainClientRegistry.stationHasTrain(pos);
        if (this.loconautics$disassembling > 0) {
            this.loconautics$disassembling--;
        }

        this.loconautics$driveArrival(pos, hasTrain);
        if (!hasTrain) {
            if (this.loconautics$showingTrain()) {
                this.newTrainButton.visible = false;
                this.newTrainButton.active = false;
                if (this.loconautics$leaving > 0) {
                    this.loconautics$hideTrainControls();
                }
            } else if (this.loconautics$forced) {
                this.loconautics$hideTrainControls();
                this.loconautics$forced = false;
            }
        } else {
            this.loconautics$forced = true;
            this.newTrainButton.visible = false;
            this.newTrainButton.active = false;
            this.disassembleTrainButton.visible = true;
            this.disassembleTrainButton.active = SableTrainClientRegistry.isStationParked(be.getBlockPos());
            this.disassembleTrainButton.withCallback(() -> {
                this.loconautics$disassembling = 60;
                CatnipServices.NETWORK.sendToServer(new DisassembleSableTrainPacket(be.getBlockPos()));
            });
            this.dropScheduleButton.visible = true;
            this.dropScheduleButton.active = false;
            this.colorTypeScroll.visible = true;
            this.colorTypeScroll.active = true;
            this.loconautics$iconScroll.visible = true;
            this.loconautics$iconScroll.active = true;
            if (!this.colorTypeScroll.isHovered()) {
                this.colorTypeScroll.setState(SableTrainClientRegistry.stationMapColorIndex(be.getBlockPos()));
            }

            if (!this.loconautics$iconScroll.isHovered()) {
                this.loconautics$iconScroll.setState(this.loconautics$iconTypes.indexOf(this.loconautics$currentIcon().getId()));
            }

            if (!this.trainNameBox.active) {
                this.trainNameBox.active = true;
                this.addRenderableWidget(this.trainNameBox);
            }

            if (!this.trainNameBox.isFocused()) {
                this.trainNameBox.setValue(SableTrainClientRegistry.stationTrainName(be.getBlockPos()));
            }
        }
    }

    @Unique
    private void loconautics$hideTrainControls() {
        this.disassembleTrainButton.visible = false;
        this.disassembleTrainButton.active = false;
        this.dropScheduleButton.visible = false;
        this.dropScheduleButton.active = false;
        this.colorTypeScroll.visible = false;
        this.colorTypeScroll.active = false;
        this.loconautics$iconScroll.visible = false;
        this.loconautics$iconScroll.active = false;
    }

    @Unique
    private void loconautics$driveArrival(BlockPos pos, boolean hasTrain) {
        if (hasTrain) {
            if (!this.loconautics$hadTrain) {
                this.loconautics$lastApproach = Float.MAX_VALUE;
            }

            this.loconautics$spans = SableTrainClientRegistry.stationCarriages(pos);
            this.loconautics$leaving = 0;
            this.loconautics$absent = 0;
            this.loconautics$hadTrain = true;
        } else if (this.loconautics$hadTrain) {
            this.loconautics$absent++;
            if (this.loconautics$absent < 5) {
                return;
            }

            this.loconautics$hadTrain = false;
            this.loconautics$leaving = this.loconautics$lastApproach <= 0.0F && this.loconautics$disassembling <= 0 ? 80 : 0;
        } else if (this.loconautics$leaving > 0) {
            this.loconautics$leaving--;
        }

        if (!hasTrain && this.loconautics$leaving <= 0) {
            this.loconautics$seeded = false;
            this.loconautics$sawEmpty = true;
            this.loconautics$lastApproach = Float.MAX_VALUE;
        } else {
            int bgWidth = AllGuiTextures.STATION.getWidth();
            int target = this.loconautics$targetPos();
            if (!hasTrain) {
                float f = 1.0F - this.loconautics$leaving / 80.0F;
                this.loconautics$trainPosition.setValue(target + f * f * f * (bgWidth - target + 5));
            } else {
                float approach = SableTrainClientRegistry.stationApproach(pos);
                if (approach >= Float.MAX_VALUE) {
                    approach = 0.0F;
                }

                approach = Math.min(approach, this.loconautics$lastApproach);
                this.loconautics$lastApproach = approach;
                float f = Mth.clamp(approach / 30.0F, 0.0F, 1.0F);
                float slide = target - (target + this.loconautics$rowWidth()) * f;
                if (this.loconautics$seeded) {
                    float shown = this.loconautics$trainPosition.getValue();
                    this.loconautics$trainPosition.setValue(shown + (slide - shown) * 0.25F);
                } else {
                    float start = this.loconautics$sawEmpty ? target - (target + this.loconautics$rowWidth()) : slide;
                    this.loconautics$trainPosition.startWithValue(start);
                    this.loconautics$seeded = true;
                }
            }
        }
    }

    @Unique
    private int loconautics$rowWidth() {
        TrainIconType icon = this.loconautics$scrolledIcon();
        int w = icon.getIconWidth(-1);

        for (int i = this.loconautics$spans.length - 1; i >= 1; i--) {
            w += icon.getIconWidth(this.loconautics$spans[i]) + 1;
        }

        return w;
    }

    @Unique
    private int loconautics$targetPos() {
        int width = this.loconautics$rowWidth();
        int target = AllGuiTextures.STATION.getWidth() / 2 - width / 2;
        return width > 130 ? target - (width - 130) : target;
    }

    @Unique
    private float loconautics$edgeFade(float position, int offset, int bgWidth) {
        return Mth.clamp(Math.min((position + offset - 10.0F) / 30.0F, (bgWidth - 40 - position - offset) / 30.0F), 0.0F, 1.0F);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void loconautics$commitNameOnEnter(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        if ((key == 257 || key == 335) && this.trainNameBox.isFocused()) {
            StationBlockEntity be = this.loconautics$be();
            if (SableTrainClientRegistry.isStationParked(be.getBlockPos())) {
                this.loconautics$sendName(be);
            }
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void loconautics$commitNameOnClose(CallbackInfo ci) {
        if (this.trainNameBox != null) {
            StationBlockEntity be = this.loconautics$be();
            if (SableTrainClientRegistry.isStationParked(be.getBlockPos())) {
                this.loconautics$sendName(be);
            }
        }
    }

    @Unique
    private TrainIconType loconautics$currentIcon() {
        String id = SableTrainClientRegistry.stationIconType(this.loconautics$be().getBlockPos());
        if (id.isEmpty()) {
            return TrainIconType.getDefault();
        }

        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? TrainIconType.getDefault() : TrainIconType.byId(parsed);
    }

    @Unique
    private TrainIconType loconautics$scrolledIcon() {
        int index = this.loconautics$iconScroll.getState();
        return index >= 0 && index < this.loconautics$iconTypes.size()
            ? TrainIconType.byId(this.loconautics$iconTypes.get(index))
            : this.loconautics$currentIcon();
    }

    @Unique
    private void loconautics$sendStyle() {
        int index = this.loconautics$iconScroll.getState();
        String iconId = index >= 0 && index < this.loconautics$iconTypes.size() ? this.loconautics$iconTypes.get(index).toString() : "";
        CatnipServices.NETWORK.sendToServer(new StationSetTrainColorPacket(this.loconautics$be().getBlockPos(), this.colorTypeScroll.getState(), iconId));
    }

    @Unique
    private void loconautics$sendName(StationBlockEntity be) {
        CatnipServices.NETWORK.sendToServer(new StationSetTrainNamePacket(be.getBlockPos(), this.trainNameBox.getValue()));
    }

    @Redirect(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"
        ),
        remap = false
    )
    private int loconautics$suppressIdleHeader(GuiGraphics graphics, Font font, Component text, int sx, int sy, int color, boolean shadow) {
        return this.loconautics$showingTrain() ? 0 : graphics.drawString(font, text, sx, sy, color, shadow);
    }

    @Inject(method = "renderWindow", at = @At("RETURN"))
    private void loconautics$renderSableTrainDisplay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.loconautics$showingTrain()) {
            int bgWidth = AllGuiTextures.STATION.getWidth();
            int x = (this.width - bgWidth) / 2;
            int y = (this.height - AllGuiTextures.STATION.getHeight()) / 2;
            int[] spans = this.loconautics$spans;
            TrainIconType icon = this.loconautics$scrolledIcon();
            float position = this.loconautics$trainPosition.getValue(partialTicks);
            PoseStack ms = graphics.pose();
            ms.pushPose();
            ms.translate(position, 0.0F, 0.0F);
            RenderSystem.enableBlend();
            int offset = 0;

            for (int i = spans.length - 1; i >= 1; i--) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.loconautics$edgeFade(position, offset, bgWidth));
                offset += icon.render(spans[i], graphics, x + offset, y + 20) + 1;
            }

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.loconautics$edgeFade(position, offset, bgWidth));
            offset += icon.render(-1, graphics, x + offset, y + 20);
            RenderSystem.disableBlend();
            ms.popPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            AllGuiTextures.STATION_TEXTBOX_TOP.render(graphics, x + 21, y + 42);
            UIRenderHelper.drawStretched(graphics, x + 21, y + 60, 150, 26, 0, AllGuiTextures.STATION_TEXTBOX_MIDDLE);
            AllGuiTextures.STATION_TEXTBOX_BOTTOM.render(graphics, x + 21, y + 86);
            int speechX = x + (int)Mth.clamp(position + offset - 13.0F, 25.0F, 159.0F);
            AllGuiTextures.STATION_TEXTBOX_SPEECH.render(graphics, speechX, y + 38);
            if (!this.trainNameBox.isFocused()) {
                String name = this.trainNameBox.getValue();
                String shown = name;
                if (name.isEmpty()) {
                    Component placeholder = CreateLang.translateDirect("train.unnamed", new Object[0]);
                    shown = placeholder.getString();
                    graphics.drawString(this.font, placeholder, this.loconautics$nameBoxX(shown), y + 47, 8421504, false);
                }

                int buttonX = this.loconautics$nameBoxX(shown) + this.font.width(shown) + 5;
                AllGuiTextures.STATION_EDIT_TRAIN_NAME.render(graphics, Math.min(buttonX, x + 156), y + 44);
            }

            this.loconautics$renderMapColorPreview(graphics);
        }
    }

    @Unique
    private boolean loconautics$showingTrain() {
        return this.loconautics$hadTrain || this.loconautics$leaving > 0;
    }

    @Unique
    private void loconautics$renderMapColorPreview(GuiGraphics graphics) {
        AllGuiTextures sprite = AllGuiTextures.TRAINMAP_SPRITES;
        sprite.bind();
        int index = this.colorTypeScroll.getState();
        int colorRow = index / 4;
        int colorCol = index % 4;
        int rotation = AnimationTickHolder.getTicks() / 5 % 8;

        for (int slice = 0; slice < 3; slice++) {
            int row = slice == 0 ? 1 : (slice == 2 ? 2 : 3);
            graphics.blit(
                sprite.location,
                this.colorTypeScroll.getX() + 4,
                this.colorTypeScroll.getY() - 1,
                rotation * 16 + colorCol * 128,
                row * 16 + colorRow * 64,
                16,
                16,
                sprite.getWidth(),
                sprite.getHeight()
            );
        }
    }

    @Unique
    private int loconautics$nameBoxX(String s) {
        int bgWidth = AllGuiTextures.STATION.getWidth();
        int x = (this.width - bgWidth) / 2;
        return x + bgWidth / 2 - (Math.min(this.font.width(s), this.trainNameBox.getWidth()) + 10) / 2;
    }
}
