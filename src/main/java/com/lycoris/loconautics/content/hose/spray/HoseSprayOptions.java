package com.lycoris.loconautics.content.hose.spray;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record HoseSprayOptions(int color, boolean suck) implements ParticleOptions {
    public HoseSprayOptions(int color) {
        this(color, false);
    }

    public static MapCodec<HoseSprayOptions> codec(ParticleType<HoseSprayOptions> type) {
        return RecordCodecBuilder.mapCodec(
            i -> i.group(
                    Codec.INT.fieldOf("color").forGetter(HoseSprayOptions::color), Codec.BOOL.optionalFieldOf("suck", false).forGetter(HoseSprayOptions::suck)
                )
                .apply(i, HoseSprayOptions::new)
        );
    }

    public static StreamCodec<RegistryFriendlyByteBuf, HoseSprayOptions> streamCodec(ParticleType<HoseSprayOptions> t) {
        return StreamCodec.composite(ByteBufCodecs.INT, HoseSprayOptions::color, ByteBufCodecs.BOOL, HoseSprayOptions::suck, HoseSprayOptions::new);
    }

    public ParticleType<?> getType() {
        return (ParticleType<?>)LoconauticsRegistries.HOSE_WATER_SPRAY.get();
    }
}
