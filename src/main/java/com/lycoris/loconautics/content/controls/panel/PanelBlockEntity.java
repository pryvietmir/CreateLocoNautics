package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panellever.LeverHoldInteraction;
import com.lycoris.loconautics.foundation.LabelText;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class PanelBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, PartialSafeNBT {
    private static final String PARTS_TAG = "Gauges";
    private static final String TYPE_KEY = "Type";
    private static final String LINKS_TAG = "LinkFrequencies";
    private static final String LABELS_TAG = "Labels";
    private static final String INVERTED_TAG = "Inverted";
    private static final String GLASS_TAG = "GlassOpen";
    private static final List<String> PICK_TAGS = List.of("Gauges", "LinkFrequencies", "Labels", "Inverted", "GlassOpen");
    private static final double BULB_CHASE_SPEED = 0.7;
    private static final long SETTLED_MILLIS = 100000L;
    private static final long HOLD_REPEAT_TICKS = 5L;
    protected final Map<PanelSlot, PanelPart> parts = new EnumMap<>(PanelSlot.class);
    private final Map<PanelSlot, LerpedFloat> animations = new EnumMap<>(PanelSlot.class);
    private final Map<PanelSlot, LerpedFloat> bulbs = new EnumMap<>(PanelSlot.class);
    private final Map<PanelSlot, PanelLinkTransmitter> transmitters = new EnumMap<>(PanelSlot.class);
    private final Map<PanelSlot, Component> labels = new EnumMap<>(PanelSlot.class);
    private final Set<PanelSlot> inverted = EnumSet.noneOf(PanelSlot.class);
    private final Set<PanelSlot> glassOpen = EnumSet.noneOf(PanelSlot.class);
    private final Map<PanelSlot, PanelBlockEntity.GlassLid> lids = new EnumMap<>(PanelSlot.class);
    private final Map<PanelSlot, Long> lastGlassUse = new EnumMap<>(PanelSlot.class);
    @Nullable
    private BlockPos assemblyShift;
    private boolean centered;

    public PanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void onAssembled(BlockPos shift) {
        this.assemblyShift = shift;
    }

    private void relocateParts() {
        BlockPos shift = this.assemblyShift;
        this.assemblyShift = null;

        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart part = this.parts.get(slot);
            if (part != null) {
                PanelPart moved = part.relocated(this.level, shift);
                if (!moved.equals(part)) {
                    this.replacePart(slot, moved);
                }
            }
        }
    }

    public void tick() {
        super.tick();
        boolean server = this.level != null && !this.level.isClientSide;
        if (server && this.assemblyShift != null) {
            this.relocateParts();
        }

        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart part = this.parts.get(slot);
            if (part != null) {
                if (server) {
                    part.serverTick(this, slot);
                    this.transmit(slot, part);
                }

                LerpedFloat animation = this.animation(slot);
                animation.chase(part.animationTarget(this), part.chaseSpeed(), Chaser.EXP);
                animation.tickChaser();
                LerpedFloat bulb = this.bulbs.computeIfAbsent(slot, k -> LerpedFloat.linear().startWithValue(0.0));
                bulb.chase(this.bulbTarget(slot, part), 0.7, Chaser.EXP);
                bulb.tickChaser();
            }
        }
    }

    private float bulbTarget(PanelSlot slot, PanelPart part) {
        return part.lightLevel(this, slot);
    }

    public float bulbGlow(PanelSlot slot, float partialTick) {
        LerpedFloat bulb = this.bulbs.get(slot);
        return bulb == null ? 0.0F : bulb.getValue(partialTick);
    }

    private void transmit(PanelSlot slot, PanelPart part) {
        PanelLinkTransmitter transmitter = this.transmitters.get(slot);
        if (transmitter != null && part instanceof RedstoneLinkedPart linked) {
            transmitter.transmit(this.level, this.outputSignal(slot, linked.signal(this.level)));
        }
    }

    @Nullable
    public PanelLinkTransmitter transmitter(PanelSlot slot) {
        return this.transmitters.get(slot);
    }

    public boolean hasLinkFrequency(PanelPart part) {
        for (PanelSlot slot : this.transmitters.keySet()) {
            if (this.parts.get(slot) == part) {
                return true;
            }
        }

        return false;
    }

    public void setLinkFrequency(PanelSlot slot, ItemStack first, ItemStack second) {
        this.dropTransmitter(slot);
        if (!PanelLinkTransmitter.isEmpty(first, second)) {
            this.transmitters.put(slot, new PanelLinkTransmitter(this, first, second));
        }

        this.notifyUpdate();
    }

    private void dropTransmitter(PanelSlot slot) {
        PanelLinkTransmitter transmitter = this.transmitters.remove(slot);
        if (transmitter != null && this.level != null) {
            transmitter.release(this.level);
        }
    }

    private void dropAllTransmitters() {
        for (PanelSlot slot : PanelSlot.values()) {
            this.dropTransmitter(slot);
        }
    }

    public void invalidate() {
        super.invalidate();
        this.dropAllTransmitters();
    }

    public LerpedFloat animation(PanelSlot slot) {
        return this.animations.computeIfAbsent(slot, k -> {
            PanelPart part = this.parts.get(k);
            return part == null ? LerpedFloat.linear().startWithValue(0.0) : part.newAnimation();
        });
    }

    public boolean hasPart(PanelSlot slot) {
        return this.parts.containsKey(slot);
    }

    public int partCount() {
        return this.parts.size();
    }

    public boolean isFull() {
        return this.parts.size() >= this.slots().length;
    }

    public PanelSlot[] slots() {
        return PanelSlot.values();
    }

    @Nullable
    public PanelPart getPart(PanelSlot slot) {
        return this.parts.get(slot);
    }

    public ItemStack stackForSlot(PanelSlot slot) {
        PanelPart part = this.parts.get(slot);
        if (part == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = part.asStack();
        Component label = this.labels.get(slot);
        if (label != null) {
            stack.set(DataComponents.CUSTOM_NAME, label);
        }

        return stack;
    }

    public boolean isInverted(PanelSlot slot) {
        return this.inverted.contains(slot);
    }

    public void setInverted(PanelSlot slot, boolean value) {
        if (this.hasPart(slot) && this.inverted.contains(slot) != value) {
            if (value) {
                this.inverted.add(slot);
            } else {
                this.inverted.remove(slot);
            }

            this.notifyUpdate();
        }
    }

    public boolean isGlassClosed(PanelSlot slot) {
        PanelPart part = this.parts.get(slot);
        return part != null && part.glass() != null && !this.glassOpen.contains(slot);
    }

    public void toggleGlassFromUse(PanelSlot slot) {
        if (this.level != null) {
            long now = this.level.getGameTime();
            Long last = this.lastGlassUse.put(slot, now);
            if (last == null || now - last > 5L) {
                this.toggleGlass(slot);
            }
        }
    }

    public void toggleGlass(PanelSlot slot) {
        this.setGlassOpen(slot, !this.glassOpen.contains(slot));
    }

    public void setGlassOpen(PanelSlot slot, boolean value) {
        PanelPart part = this.parts.get(slot);
        if (part != null && part.glass() != null && this.glassOpen.contains(slot) != value) {
            if (value) {
                this.glassOpen.add(slot);
            } else {
                this.glassOpen.remove(slot);
            }

            if (this.level != null && !this.level.isClientSide) {
                this.level
                    .playSound(
                        null, this.worldPosition, value ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 1.4F
                    );
            }

            this.notifyUpdate();
        }
    }

    public float glassAngle(PanelSlot slot) {
        boolean open = this.glassOpen.contains(slot);
        long now = Util.getMillis();
        PanelBlockEntity.GlassLid lid = this.lids.get(slot);
        if (lid == null) {
            lid = new PanelBlockEntity.GlassLid(open, now - 100000L);
        } else if (lid.open() != open) {
            lid = new PanelBlockEntity.GlassLid(open, now);
        }

        this.lids.put(slot, lid);
        return PanelGlassLid.angle(lid.open(), (float)(now - lid.startMillis()) / 1000.0F);
    }

    public int outputSignal(PanelSlot slot, int signal) {
        return this.inverted.contains(slot) ? 15 - signal : signal;
    }

    @Nullable
    public Component label(PanelSlot slot) {
        return this.labels.get(slot);
    }

    public void setLabel(PanelSlot slot, @Nullable Component label) {
        if (this.hasPart(slot)) {
            if (label == null) {
                this.labels.remove(slot);
            } else {
                this.labels.put(slot, LabelText.parse(label));
            }

            this.notifyUpdate();
        }
    }

    @Nullable
    public PanelSlot firstFreeSlot(PanelSlot preferred) {
        if (!this.hasPart(preferred)) {
            return preferred;
        }

        for (PanelSlot slot : this.slots()) {
            if (!this.hasPart(slot)) {
                return slot;
            }
        }

        return null;
    }

    public boolean isCentered() {
        return this.centered && this.parts.size() == 1;
    }

    @Nullable
    public PanelSlot soleSlot() {
        return this.isCentered() ? this.parts.keySet().iterator().next() : null;
    }

    private void vacateCenter(PanelSlot incoming) {
        PanelSlot sole = this.soleSlot();
        this.centered = false;
        if (sole != null && sole != PanelSlot.TOP_LEFT && incoming != PanelSlot.TOP_LEFT) {
            this.parts.put(PanelSlot.TOP_LEFT, this.parts.remove(sole));
            LerpedFloat animation = this.animations.remove(sole);
            if (animation != null) {
                this.animations.put(PanelSlot.TOP_LEFT, animation);
            }

            LerpedFloat bulb = this.bulbs.remove(sole);
            if (bulb != null) {
                this.bulbs.put(PanelSlot.TOP_LEFT, bulb);
            }

            Component label = this.labels.remove(sole);
            if (label != null) {
                this.labels.put(PanelSlot.TOP_LEFT, label);
            }

            if (this.inverted.remove(sole)) {
                this.inverted.add(PanelSlot.TOP_LEFT);
            }

            if (this.glassOpen.remove(sole)) {
                this.glassOpen.add(PanelSlot.TOP_LEFT);
            }

            this.lids.remove(sole);
            PanelLinkTransmitter transmitter = this.transmitters.remove(sole);
            if (transmitter != null) {
                this.transmitters.put(PanelSlot.TOP_LEFT, transmitter);
            }
        }
    }

    public boolean putPart(PanelSlot slot, PanelPart part, boolean center) {
        if (this.hasPart(slot)) {
            return false;
        }

        this.vacateCenter(slot);
        if (this.parts.isEmpty()) {
            this.centered = center;
        }

        this.parts.put(slot, part);
        this.syncSlots();
        return true;
    }

    public boolean putPart(PanelSlot slot, PanelPart part) {
        return this.putPart(slot, part, false);
    }

    public void replacePart(PanelSlot slot, PanelPart part) {
        if (this.hasPart(slot)) {
            this.parts.put(slot, part);
            this.notifyUpdate();
        }
    }

    public boolean removePart(PanelSlot slot) {
        PanelPart removed = this.parts.remove(slot);
        if (removed == null) {
            return false;
        }

        if (this.level != null && !this.level.isClientSide) {
            removed.onRemoved(this);
        }

        this.dropTransmitter(slot);
        this.animations.remove(slot);
        this.bulbs.remove(slot);
        this.labels.remove(slot);
        this.inverted.remove(slot);
        this.glassOpen.remove(slot);
        this.lids.remove(slot);
        this.lastGlassUse.remove(slot);
        this.syncSlots();
        return true;
    }

    public void releaseParts() {
        if (this.level != null && !this.level.isClientSide) {
            this.parts.values().forEach(part -> part.onRemoved(this));
            this.dropAllTransmitters();
        }
    }

    protected void syncSlots() {
        if (this.level != null) {
            BlockState state = this.getBlockState();

            for (PanelSlot slot : PanelSlot.values()) {
                if (state.hasProperty(slot.property())) {
                    state = (BlockState)state.setValue(slot.property(), this.hasPart(slot));
                }
            }

            if (state != this.getBlockState()) {
                this.level.setBlock(this.worldPosition, state, 3);
            }

            this.notifyUpdate();
        }
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (this.parts.isEmpty()) {
            return false;
        }

        if (Minecraft.getInstance().screen != null) {
            return false;
        }

        if (LeverHoldInteraction.gripped()) {
            return false;
        }

        PanelSlot looked = this.lookedAtSlot();
        PanelPart lookedPart = looked == null ? null : this.parts.get(looked);
        if (lookedPart != null) {
            this.addPartTooltip(looked, lookedPart, tooltip);
        } else {
            this.parts.forEach((slot, part) -> this.addPartTooltip(slot, part, tooltip));
        }

        return true;
    }

    private void addPartTooltip(PanelSlot slot, PanelPart part, List<Component> tooltip) {
        int title = tooltip.size();
        part.addToGoggleTooltip(this, tooltip);
        Component label = this.labels.get(slot);
        if (label != null && tooltip.size() > title) {
            tooltip.add(title + 1, Component.literal("    ").append(label));
        }

        if (this.inverted.contains(slot) && tooltip.size() > title) {
            LoconauticsLang.translate("gui.goggles.panel_inverted").style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
    }

    @Nullable
    private PanelSlot lookedAtSlot() {
        if (Minecraft.getInstance().hitResult instanceof BlockHitResult hit) {
            if (!hit.getBlockPos().equals(this.worldPosition)) {
                return null;
            } else {
                return this.getBlockState().getBlock() instanceof PanelHost host
                    ? host.slotFor(this, this.worldPosition, this.getBlockState(), hit.getLocation())
                    : null;
            }
        } else {
            return null;
        }
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        CompoundTag slots = new CompoundTag();
        this.parts.forEach((slot, part) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Type", part.type().getSerializedName());
            part.write(entry);
            slots.put(slot.getSerializedName(), entry);
        });
        tag.put("Gauges", slots);
        CompoundTag links = new CompoundTag();
        this.transmitters.forEach((slot, transmitter) -> links.put(slot.getSerializedName(), transmitter.write(registries)));
        tag.put("LinkFrequencies", links);
        CompoundTag names = new CompoundTag();
        this.labels.forEach((slot, label) -> names.putString(slot.getSerializedName(), Serializer.toJson(label, registries)));
        tag.put("Labels", names);
        CompoundTag flipped = new CompoundTag();
        this.inverted.forEach(slot -> flipped.putBoolean(slot.getSerializedName(), true));
        tag.put("Inverted", flipped);
        CompoundTag opened = new CompoundTag();
        this.glassOpen.forEach(slot -> opened.putBoolean(slot.getSerializedName(), true));
        tag.put("GlassOpen", opened);
        tag.putBoolean("Centered", this.centered);
    }

    public void writeSafe(CompoundTag tag, Provider registries) {
        this.write(tag, registries, false);
    }

    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        PanelSlot picked = this.level != null && this.level.isClientSide ? this.lookedAtSlot() : null;

        for (String key : PICK_TAGS) {
            if (picked == null) {
                tag.remove(key);
            } else {
                tag.getCompound(key).getAllKeys().removeIf(slot -> !slot.equals(picked.getSerializedName()));
            }
        }

        tag.remove("Centered");
        if (picked != null) {
            tag.remove("x");
            tag.remove("y");
            tag.remove("z");
        }

        LoconauticsConstants.LOGGER
            .debug(
                "[panelpick] strip clientSide={} picked={} keys={} parts={}",
                new Object[]{this.level != null && this.level.isClientSide, picked, tag.getAllKeys(), tag.getCompound("Gauges")}
            );
    }

    public void applyPicked(PanelSlot target, CompoundTag picked, Provider registries) {
        CompoundTag slots = picked.getCompound("Gauges");
        LoconauticsConstants.LOGGER.debug("[panelpick] apply target={} keys={} parts={}", new Object[]{target, picked.getAllKeys(), slots});
        if (slots.getAllKeys().size() == 1) {
            String source = (String)slots.getAllKeys().iterator().next();
            CompoundTag entry = slots.getCompound(source);
            PanelPart current = this.parts.get(target);
            PanelPartType type = PanelPartType.byName(entry.getString("Type"));
            LoconauticsConstants.LOGGER.debug("[panelpick] types picked={} current={}", type, current == null ? null : current.type());
            if (current != null && type == current.type()) {
                PanelPart part = type.read(entry);
                if (part != null) {
                    this.replacePart(target, part);
                }

                this.setInverted(target, picked.getCompound("Inverted").getBoolean(source));
                this.setGlassOpen(target, picked.getCompound("GlassOpen").getBoolean(source));
                CompoundTag names = picked.getCompound("Labels");
                if (names.contains(source)) {
                    Component label = Serializer.fromJson(names.getString(source), registries);
                    if (label != null) {
                        this.setLabel(target, label);
                    }
                }

                CompoundTag links = picked.getCompound("LinkFrequencies");
                if (links.contains(source)) {
                    this.dropTransmitter(target);
                    this.transmitters.put(target, PanelLinkTransmitter.read(this, links.getCompound(source), registries));
                    this.notifyUpdate();
                }
            }
        }
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        Map<PanelSlot, PanelPartType> before = new EnumMap<>(PanelSlot.class);
        this.parts.forEach((slotx, part) -> before.put(slotx, part.type()));
        this.centered = tag.getBoolean("Centered");
        this.parts.clear();
        CompoundTag slots = tag.getCompound("Gauges");

        for (PanelSlot slot : PanelSlot.values()) {
            String key = slot.getSerializedName();
            if (slots.contains(key)) {
                CompoundTag entry = slots.getCompound(key);
                PanelPartType partType = PanelPartType.byName(entry.getString("Type"));
                if (partType != null) {
                    PanelPart part = partType.read(entry);
                    if (part != null) {
                        this.parts.put(slot, part);
                    }
                }
            }
        }

        this.inverted.clear();
        CompoundTag flipped = tag.getCompound("Inverted");

        for (PanelSlot slot : PanelSlot.values()) {
            if (flipped.getBoolean(slot.getSerializedName()) && this.parts.containsKey(slot)) {
                this.inverted.add(slot);
            }
        }

        this.glassOpen.clear();
        CompoundTag opened = tag.getCompound("GlassOpen");

        for (PanelSlot slot : PanelSlot.values()) {
            if (opened.getBoolean(slot.getSerializedName()) && this.parts.containsKey(slot)) {
                this.glassOpen.add(slot);
            }
        }

        this.labels.clear();
        CompoundTag names = tag.getCompound("Labels");

        for (PanelSlot slot : PanelSlot.values()) {
            String key = slot.getSerializedName();
            if (names.contains(key) && this.parts.containsKey(slot)) {
                Component label = Serializer.fromJson(names.getString(key), registries);
                if (label != null) {
                    this.labels.put(slot, label);
                }
            }
        }

        this.dropAllTransmitters();
        CompoundTag links = tag.getCompound("LinkFrequencies");

        for (PanelSlot slot : PanelSlot.values()) {
            String key = slot.getSerializedName();
            if (links.contains(key)) {
                this.transmitters.put(slot, PanelLinkTransmitter.read(this, links.getCompound(key), registries));
            }
        }

        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart now = this.parts.get(slot);
            if (now == null || before.get(slot) != now.type()) {
                this.animations.remove(slot);
                this.bulbs.remove(slot);
            }
        }

        LoconauticsConstants.LOGGER
            .debug(
                "[panelcopy] read pos={} clientPacket={} level={} clientSide={} parts={} state={}",
                new Object[]{
                    this.worldPosition,
                    clientPacket,
                    this.level != null,
                    this.level != null && this.level.isClientSide,
                    this.parts.keySet(),
                    this.getBlockState()
                }
            );
        if (!clientPacket && this.level != null && !this.level.isClientSide) {
            this.notifyUpdate();
        }

        if (!clientPacket && this.level != null && !this.level.isClientSide && this.parts.isEmpty()) {
            LoconauticsConstants.LOGGER.debug("[panelcopy] empty server read keys={}", tag.getAllKeys(), new Throwable());
        }
    }

    private record GlassLid(boolean open, long startMillis) {
    }
}
