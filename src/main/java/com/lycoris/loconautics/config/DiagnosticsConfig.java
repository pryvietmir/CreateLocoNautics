package com.lycoris.loconautics.config;

import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;

public final class DiagnosticsConfig {
    public static BooleanValue DISABLE_ALL;

    private DiagnosticsConfig() {
    }

    public static void define(Builder builder) {
        DISABLE_ALL = builder.comment("Turn off every diagnostics channel below at once").define("disableAll", false);

        for (DiagnosticsConfig.Channel channel : DiagnosticsConfig.Channel.values()) {
            channel.define(builder);
        }
    }

    public enum Channel {
        FORCE("force", true),
        FORCE_LINES("forceLines", false);

        private final String key;
        private final boolean alwaysOn;
        private BooleanValue value;

        Channel(String key, boolean alwaysOn) {
            this.key = key;
            this.alwaysOn = alwaysOn;
        }

        private void define(Builder builder) {
            this.value = builder.define(this.key, this.alwaysOn);
        }

        public boolean enabled() {
            return (this.alwaysOn || (Boolean)this.value.get()) && !(Boolean)DiagnosticsConfig.DISABLE_ALL.get();
        }

        public boolean toggle() {
            boolean next = !(Boolean)this.value.get();
            this.value.set(next);
            return next;
        }
    }
}
