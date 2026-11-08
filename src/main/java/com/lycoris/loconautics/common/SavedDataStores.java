package com.lycoris.loconautics.common;

import java.util.Map;
import net.minecraft.world.level.saveddata.SavedData;

public final class SavedDataStores {
    private SavedDataStores() {
    }

    public static <K, V> void remove(SavedData store, Map<K, V> entries, K key) {
        if (entries.remove(key) != null) {
            store.setDirty();
        }
    }
}
