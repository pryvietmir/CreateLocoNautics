package com.lycoris.loconautics.compat.heat;

import net.neoforged.fml.ModList;

/**
 * Presence check for Create Propulsion that never touches Propulsion classes. Kept apart from
 * {@link PropulsionEngineHeat}: loading that class makes the JVM verify code that references
 * Propulsion types, which crashes with NoClassDefFoundError when the mod isn't installed.
 */
public final class PropulsionCompat {
    public static final String MOD_ID = "createpropulsion";

    private PropulsionCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
