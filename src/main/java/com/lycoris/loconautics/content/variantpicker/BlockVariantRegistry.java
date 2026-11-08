package com.lycoris.loconautics.content.variantpicker;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.variantpicker.data.LoadedVariantFamily;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class BlockVariantRegistry {
    public static final int COLOR_COUNT = 17;
    public static final int COLORLESS_INDEX = 16;
    public static final int MAX_TYPES = 16;
    private static final List<List<BlockVariantRegistry.VariantType>> FAMILIES = new ArrayList<>();
    private static final Map<Block, BlockVariantRegistry.Location> INDEX = new HashMap<>();

    private BlockVariantRegistry() {
    }

    public static synchronized void rebuild(List<LoadedVariantFamily> datapack) {
        FAMILIES.clear();
        INDEX.clear();

        for (LoadedVariantFamily fam : datapack) {
            registerLoaded(fam);
        }
    }

    private static void registerLoaded(LoadedVariantFamily fam) {
        List<BlockVariantRegistry.VariantType> types = new ArrayList<>();

        for (LoadedVariantFamily.Type lt : fam.types()) {
            Block[] colors = new Block[17];
            boolean anyColor = false;

            for (Entry<Integer, ResourceLocation> e : lt.colors().entrySet()) {
                Block b = (Block)BuiltInRegistries.BLOCK.getOptional(e.getValue()).orElse(null);
                if (b == null) {
                    LoconauticsConstants.LOGGER.warn("[VariantPicker] block {} is not registered, skipped", e.getValue());
                } else if (e.getKey() >= 0 && e.getKey() < 17) {
                    colors[e.getKey()] = b;
                    anyColor = true;
                }
            }

            if (anyColor) {
                int matCount = lt.materials().size();
                Block[][] wrapped = matCount > 0 ? new Block[17][matCount] : null;
                if (wrapped != null) {
                    for (Entry<Integer, Map<Integer, ResourceLocation>> ce : lt.wrapped().entrySet()) {
                        if (ce.getKey() >= 0 && ce.getKey() < 17) {
                            for (Entry<Integer, ResourceLocation> me : ce.getValue().entrySet()) {
                                Block b = (Block)BuiltInRegistries.BLOCK.getOptional(me.getValue()).orElse(null);
                                if (b != null && me.getKey() >= 0 && me.getKey() < matCount) {
                                    wrapped[ce.getKey()][me.getKey()] = b;
                                }
                            }
                        }
                    }
                }

                types.add(new BlockVariantRegistry.VariantType(lt.name(), colors, wrapped, matCount));
            }
        }

        for (int i = 0; i < types.size(); i += 16) {
            registerFamily(types.subList(i, Math.min(i + 16, types.size())));
        }
    }

    private static void registerFamily(List<BlockVariantRegistry.VariantType> types) {
        if (!types.isEmpty()) {
            int familyIdx = FAMILIES.size();
            FAMILIES.add(List.copyOf(types));

            for (int t = 0; t < types.size(); t++) {
                BlockVariantRegistry.VariantType type = types.get(t);
                Block[] colors = type.colorVariants();

                for (int c = 0; c < colors.length; c++) {
                    if (colors[c] != null) {
                        INDEX.putIfAbsent(colors[c], new BlockVariantRegistry.Location(familyIdx, t, c, -1));
                    }

                    for (int m = 0; m < type.materialCount(); m++) {
                        Block w = type.wrappedAt(c, m);
                        if (w != null) {
                            INDEX.putIfAbsent(w, new BlockVariantRegistry.Location(familyIdx, t, c, m));
                        }
                    }
                }
            }
        }
    }

    public static BlockVariantRegistry.Location locate(Block block) {
        return INDEX.get(block);
    }

    public static List<BlockVariantRegistry.VariantType> family(int index) {
        return FAMILIES.get(index);
    }

    public static boolean isKnownVariant(Block block) {
        return INDEX.containsKey(block);
    }

    public record Location(int family, int type, int color, int material) {
    }

    public record VariantType(String translationKey, Block[] colorVariants, Block[][] wrapped, int materialCount) {
        public Block at(int colorIndex) {
            return colorIndex >= 0 && colorIndex < this.colorVariants.length ? this.colorVariants[colorIndex] : null;
        }

        public boolean hasWrappings() {
            return this.materialCount > 0 && this.wrapped != null;
        }

        public Block wrappedAt(int colorIndex, int materialIndex) {
            if (!this.hasWrappings()) {
                return null;
            } else if (colorIndex >= 0 && colorIndex < this.wrapped.length) {
                Block[] row = this.wrapped[colorIndex];
                return row != null && materialIndex >= 0 && materialIndex < row.length ? row[materialIndex] : null;
            } else {
                return null;
            }
        }
    }
}
