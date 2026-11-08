package com.lycoris.loconautics.content.frequency;

import java.util.List;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class FrequencyBindUtil {
    public static final String TAG = "BoundFrequencies";

    private FrequencyBindUtil() {
    }

    public static FrequencyBindUtil.Result bind(ItemStack target, Provider registries, List<FrequencyBindUtil.Pair> pairs) {
        int present = 0;

        for (FrequencyBindUtil.Pair p : pairs) {
            if (p.isPresent()) {
                present++;
            }
        }

        if (present == 0) {
            return FrequencyBindUtil.Result.EMPTY;
        }

        CompoundTag tag = ((CustomData)target.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
        CompoundTag bound = tag.getCompound("BoundFrequencies");

        for (FrequencyBindUtil.Pair p : pairs) {
            CompoundTag entry = new CompoundTag();
            entry.put("First", p.first().saveOptional(registries));
            entry.put("Second", p.second().saveOptional(registries));
            bound.put(p.key(), entry);
        }

        tag.put("BoundFrequencies", bound);
        target.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        target.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        return present == pairs.size() ? FrequencyBindUtil.Result.FULL : FrequencyBindUtil.Result.PARTIAL;
    }

    private static CompoundTag pairsToTag(Provider registries, List<FrequencyBindUtil.Pair> pairs) {
        CompoundTag root = new CompoundTag();

        for (FrequencyBindUtil.Pair p : pairs) {
            CompoundTag entry = new CompoundTag();
            entry.put("First", p.first().saveOptional(registries));
            entry.put("Second", p.second().saveOptional(registries));
            root.put(p.key(), entry);
        }

        return root;
    }

    public static ItemStack[] read(ItemStack stack, Provider registries, String key) {
        CompoundTag tag = ((CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag().getCompound("BoundFrequencies");
        if (!tag.contains(key)) {
            return null;
        }

        CompoundTag entry = tag.getCompound(key);
        return new ItemStack[]{
            ItemStack.parseOptional(registries, entry.getCompound("First")), ItemStack.parseOptional(registries, entry.getCompound("Second"))
        };
    }

    public static boolean isBound(ItemStack stack) {
        return ((CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag().contains("BoundFrequencies");
    }

    public static boolean hasGlint(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
    }

    public static boolean conflicts(ItemStack stack, Provider registries, List<FrequencyBindUtil.Pair> pairs) {
        CompoundTag stored = ((CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag().getCompound("BoundFrequencies");
        CompoundTag incoming = pairsToTag(registries, pairs);

        for (FrequencyBindUtil.Pair p : pairs) {
            if (stored.contains(p.key()) && !stored.getCompound(p.key()).equals(incoming.getCompound(p.key()))) {
                return true;
            }
        }

        return false;
    }

    public static boolean unbind(ItemStack stack) {
        boolean changed = false;
        CompoundTag tag = ((CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).copyTag();
        if (tag.contains("BoundFrequencies")) {
            tag.remove("BoundFrequencies");
            if (tag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }

            changed = true;
        }

        if (hasGlint(stack)) {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            changed = true;
        }

        return changed;
    }

    public record Pair(String key, ItemStack first, ItemStack second) {
        public boolean isPresent() {
            return !this.first.isEmpty() || !this.second.isEmpty();
        }
    }

    public enum Result {
        EMPTY,
        PARTIAL,
        FULL;
    }
}
