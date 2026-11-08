package com.lycoris.loconautics.common.turntable.sequence;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;

public enum TurntableSpeedModifier {
    FORWARD_FAST(2, ">>"),
    FORWARD(1, "->"),
    BACK(-1, "<-"),
    BACK_FAST(-2, "<<");

    public static final StreamCodec<ByteBuf, TurntableSpeedModifier> STREAM_CODEC = CatnipStreamCodecBuilders.ofEnum(TurntableSpeedModifier.class);
    public final int value;
    public final Component label;
    public final String translationKey;

    TurntableSpeedModifier(int value, String label) {
        this.value = value;
        this.label = Component.literal(label);
        this.translationKey = "loconautics.gui.turntable_bearing.speed." + Lang.asId(this.name());
    }

    public static List<Component> getOptions() {
        List<Component> options = new ArrayList<>();

        for (TurntableSpeedModifier entry : values()) {
            options.add(Component.translatable(entry.translationKey));
        }

        return options;
    }
}
