package com.lycoris.loconautics.content.relocator;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

public final class RelocatorItemData {
    public static final String TYPE_CONSIST = "CONSIST";
    public static final String TYPE_CARRIAGE = "CARRIAGE";
    public static final String TYPE_STORED = "STORED";
    private static final String KEY_TYPE = "RelocatorType";
    private static final String KEY_TRAIN = "TrainId";
    private static final String KEY_NAME = "TrainName";
    private static final String KEY_CONSIST = "ConsistOrder";
    private static final String KEY_CARRIAGE = "CarriageId";
    private static final String KEY_LABEL = "CarriageLabel";
    private static final String KEY_STRUCTURES = "Structures";
    private static final String KEY_SPAN = "Span";

    private RelocatorItemData() {
    }

    public static ItemStack forConsist(UUID trainId, @Nullable String trainName, List<UUID> order) {
        CompoundTag tag = base("CONSIST", trainId, trainName);
        ListTag list = new ListTag();

        for (UUID id : order) {
            list.add(StringTag.valueOf(id.toString()));
        }

        tag.put("ConsistOrder", list);
        return build(tag, trainName, order.size() + " carriage(s)");
    }

    public static ItemStack forCarriage(UUID trainId, @Nullable String trainName, UUID subLevelId, String label) {
        CompoundTag tag = base("CARRIAGE", trainId, trainName);
        tag.putUUID("CarriageId", subLevelId);
        tag.putString("CarriageLabel", label);
        return build(tag, trainName, "carriage " + label);
    }

    public static ItemStack forStored(UUID trainId, @Nullable String trainName, List<CompoundTag> structures, double span) {
        CompoundTag tag = base("STORED", trainId, trainName);
        ListTag list = new ListTag();
        list.addAll(structures);
        tag.put("Structures", list);
        tag.putDouble("Span", span);
        return build(tag, trainName, structures.size() + " carriage(s), right click a track to place");
    }

    private static CompoundTag base(String type, UUID trainId, @Nullable String trainName) {
        CompoundTag tag = new CompoundTag();
        tag.putString("RelocatorType", type);
        tag.putUUID("TrainId", trainId);
        if (trainName != null && !trainName.isBlank()) {
            tag.putString("TrainName", trainName);
        }

        return tag;
    }

    private static ItemStack build(CompoundTag tag, @Nullable String trainName, String subtitle) {
        ItemStack stack = new ItemStack((ItemLike)LoconauticsRegistries.CREATIVE_TRAIN_RELOCATOR.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(
            DataComponents.CUSTOM_NAME,
            Component.literal(trainName != null && !trainName.isBlank() ? trainName : "Stored Train")
                .withStyle(ChatFormatting.AQUA)
                .withStyle(Style.EMPTY.withItalic(false))
        );
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(subtitle).withStyle(ChatFormatting.GRAY))));
        return stack;
    }

    @Nullable
    public static CompoundTag read(ItemStack stack) {
        CustomData data = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }

        CompoundTag tag = data.copyTag();
        return tag.contains("RelocatorType") ? tag : null;
    }

    public static boolean isStored(CompoundTag tag) {
        return "STORED".equals(tag.getString("RelocatorType"));
    }

    public static List<CompoundTag> structures(CompoundTag tag) {
        List<CompoundTag> out = new ArrayList<>();
        ListTag list = tag.getList("Structures", 10);

        for (int i = 0; i < list.size(); i++) {
            out.add(list.getCompound(i));
        }

        return out;
    }

    public static double span(CompoundTag tag) {
        return tag.getDouble("Span");
    }

    public static boolean isConsist(CompoundTag tag) {
        return "CONSIST".equals(tag.getString("RelocatorType"));
    }

    @Nullable
    public static UUID trainId(CompoundTag tag) {
        return tag.hasUUID("TrainId") ? tag.getUUID("TrainId") : null;
    }

    @Nullable
    public static UUID carriageId(CompoundTag tag) {
        return tag.hasUUID("CarriageId") ? tag.getUUID("CarriageId") : null;
    }

    @Nullable
    public static String name(CompoundTag tag) {
        return tag.contains("TrainName") ? tag.getString("TrainName") : null;
    }

    public static String label(CompoundTag tag) {
        return tag.getString("CarriageLabel");
    }

    public static List<UUID> consistOrder(CompoundTag tag) {
        List<UUID> out = new ArrayList<>();
        ListTag list = tag.getList("ConsistOrder", 8);

        for (int i = 0; i < list.size(); i++) {
            try {
                out.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException var5) {
            }
        }

        return out;
    }
}
