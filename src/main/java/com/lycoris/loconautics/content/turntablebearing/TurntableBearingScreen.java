package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstructionType;
import com.lycoris.loconautics.common.turntable.sequence.TurntableSpeedModifier;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.lycoris.loconautics.network.packets.turntablebearing.TurntableConfigurePacket;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.GuiGameElement.GuiRenderBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.network.PacketDistributor;

public class TurntableBearingScreen extends AbstractSimiScreen {
    private static final int ROW_HEIGHT = 22;
    private final ItemStack renderedItem = new ItemStack((ItemLike)LoconauticsRegistries.TURNTABLE_BEARING_ITEM.get());
    private final AllGuiTextures background = AllGuiTextures.SEQUENCER;
    private final TurntableBearingBlockEntity blockEntity;
    private final List<TurntableInstruction> instructions;
    private final List<List<ScrollInput>> inputs = new ArrayList<>();
    private TurntableTriggerMode trigger;
    private ScrollInput triggerInput;

    public TurntableBearingScreen(TurntableBearingBlockEntity blockEntity) {
        super(Component.translatable("loconautics.gui.turntable_bearing.title"));
        this.blockEntity = blockEntity;
        this.instructions = new ArrayList<>(blockEntity.getInstructions());
        this.trigger = blockEntity.getTrigger();
    }

    protected void init() {
        this.setWindowSize(this.background.getWidth(), this.background.getHeight());
        this.setWindowOffset(-20, 0);
        super.init();
        int x = this.guiLeft;
        int y = this.guiTop;
        this.inputs.clear();

        for (int row = 0; row < 5; row++) {
            this.inputs.add(new ArrayList<>(3));
        }

        for (int row = 0; row < this.instructions.size(); row++) {
            this.initInputsOfRow(row, x, y);
        }

        this.triggerInput = new SelectionScrollInput(x + 30, y + this.background.getHeight() - 27, 90, 18)
            .forOptions(TurntableTriggerMode.getOptions())
            .calling(state -> this.trigger = TurntableTriggerMode.values()[state])
            .setState(this.trigger.ordinal())
            .titled(Component.translatable("loconautics.gui.turntable_bearing.trigger"));
        this.addRenderableWidget(this.triggerInput);
        this.refreshScheduleLock();
        IconButton confirmButton = new IconButton(x + this.background.getWidth() - 33, y + this.background.getHeight() - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        this.addRenderableWidget(confirmButton);
    }

    private void initInputsOfRow(int row, int backgroundX, int backgroundY) {
        int x = backgroundX + 30;
        int y = backgroundY + 20;
        List<ScrollInput> rowInputs = this.inputs.get(row);
        rowInputs.forEach(x$0 -> this.removeWidget(x$0));
        rowInputs.clear();
        TurntableInstruction instruction = this.instructions.get(row);
        int index = row;
        ScrollInput type = new SelectionScrollInput(x, y + 22 * row, 50, 18)
            .forOptions(TurntableInstructionType.getOptions())
            .calling(state -> this.instructionUpdated(index, state))
            .setState(instruction.type.ordinal())
            .titled(Component.translatable("loconautics.gui.turntable_bearing.instruction"));
        ScrollInput value = new ScrollInput(x + 58, y + 22 * row, 28, 18).calling(state -> instruction.value = state);
        ScrollInput modifier = new SelectionScrollInput(x + 88, y + 22 * row, 28, 18)
            .forOptions(TurntableSpeedModifier.getOptions())
            .calling(state -> instruction.modifier = TurntableSpeedModifier.values()[state])
            .titled(Component.translatable("loconautics.gui.turntable_bearing.speed"));
        rowInputs.add(type);
        rowInputs.add(value);
        rowInputs.add(modifier);
        rowInputs.forEach(x$0 -> {
            ScrollInput var10000 = (ScrollInput)this.addRenderableWidget(x$0);
        });
        this.updateParamsOfRow(row);
    }

    private void updateParamsOfRow(int row) {
        TurntableInstruction instruction = this.instructions.get(row);
        List<ScrollInput> rowInputs = this.inputs.get(row);
        TurntableInstructionType def = instruction.type;
        ScrollInput value = rowInputs.get(1);
        value.active = value.visible = def.hasValueParameter;
        if (def.hasValueParameter) {
            value.withRange(1, def.maxValue + 1)
                .titled(Component.translatable(def.parameterKey))
                .withShiftStep(def.shiftStep)
                .setState(instruction.value)
                .onChanged();
        }

        if (def == TurntableInstructionType.DELAY) {
            value.withStepFunction(context -> {
                int v = context.currentValue;
                if (!context.forward) {
                    v--;
                }

                return v < 20 ? context.shift ? 20 : 1 : context.shift ? 100 : 20;
            });
        } else {
            value.withStepFunction(value.standardStep());
        }

        ScrollInput modifier = rowInputs.get(2);
        modifier.active = modifier.visible = def.hasSpeedParameter;
        if (def.hasSpeedParameter) {
            modifier.setState(instruction.modifier.ordinal());
        }
    }

    private void refreshScheduleLock() {
        boolean locked = TurntableInstruction.isScheduleDriven(this.instructions);
        this.triggerInput.active = this.triggerInput.visible = !locked;
    }

    private void collapseToSchedule() {
        for (int i = this.instructions.size() - 1; i > 0; i--) {
            this.instructions.remove(i);
            List<ScrollInput> rowInputs = this.inputs.get(i);
            rowInputs.forEach(x$0 -> this.removeWidget(x$0));
            rowInputs.clear();
        }

        this.instructions.get(0).type = TurntableInstructionType.SCHEDULE;
        this.initInputsOfRow(0, this.guiLeft, this.guiTop);
        this.refreshScheduleLock();
    }

    private void instructionUpdated(int index, int state) {
        TurntableInstructionType newValue = TurntableInstructionType.values()[state];
        TurntableInstruction instruction = this.instructions.get(index);
        instruction.type = newValue;
        instruction.value = newValue.defaultValue;
        this.updateParamsOfRow(index);
        if (newValue == TurntableInstructionType.SCHEDULE) {
            this.collapseToSchedule();
        } else {
            this.refreshScheduleLock();
            if (newValue != TurntableInstructionType.END) {
                if (index + 1 < 5 && index + 1 == this.instructions.size()) {
                    this.instructions.add(new TurntableInstruction(TurntableInstructionType.END));
                    this.initInputsOfRow(index + 1, this.guiLeft, this.guiTop);
                }
            } else {
                for (int i = this.instructions.size() - 1; i > index; i--) {
                    this.instructions.remove(i);
                    List<ScrollInput> rowInputs = this.inputs.get(i);
                    rowInputs.forEach(x$0 -> this.removeWidget(x$0));
                    rowInputs.clear();
                }
            }
        }
    }

    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = this.guiLeft;
        int y = this.guiTop;
        this.background.render(graphics, x, y);

        for (int row = 0; row < 5; row++) {
            int yOffset = AllGuiTextures.SEQUENCER_EMPTY.getHeight() * row;
            if (row >= this.instructions.size()) {
                AllGuiTextures.SEQUENCER_EMPTY.render(graphics, x, y + 16 + yOffset);
            } else {
                TurntableInstruction instruction = this.instructions.get(row);
                TurntableInstructionType def = instruction.type;
                def.background.render(graphics, x, y + 16 + yOffset);
                this.label(graphics, 36, yOffset - 1, Component.translatable(def.translationKey));
                if (def.hasValueParameter) {
                    String text = def.formatValue(instruction.value);
                    this.label(graphics, 90 + (12 - this.font.width(text) / 2), yOffset - 1, Component.literal(text));
                }

                if (def.hasSpeedParameter) {
                    this.label(graphics, 127, yOffset - 1, instruction.modifier.label);
                }
            }
        }

        graphics.drawString(this.font, this.title, x + (this.background.getWidth() - 8) / 2 - this.font.width(this.title) / 2, y + 4, 5841956, false);
        ((GuiRenderBuilder)GuiGameElement.of(this.renderedItem).at(x + this.background.getWidth() + 6, y + this.background.getHeight() - 56, 100.0F))
            .scale(5.0)
            .render(graphics);
    }

    private void label(GuiGraphics graphics, int x, int y, Component text) {
        graphics.drawString(this.font, text, this.guiLeft + x, this.guiTop + 26 + y, 16777198);
    }

    public void removed() {
        PacketDistributor.sendToServer(
            new TurntableConfigurePacket(this.blockEntity.getBlockPos(), this.instructions, this.trigger), new CustomPacketPayload[0]
        );
    }
}
