package com.lycoris.loconautics.content.casingpicker;

import com.lycoris.loconautics.compat.railways.RailwaysBogeyChoice;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.bogey.BogeySizes.BogeySize;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

public record BogeyChoice(BogeyStyle style, BogeySize size) {
    public static final BogeyChoice CREATE_STANDARD = new BogeyChoice(AllBogeyStyles.STANDARD, BogeySizes.SMALL);
    public static final Codec<BogeyChoice> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("style").forGetter(choice -> choice.style().id),
                ResourceLocation.CODEC.fieldOf("size").forGetter(choice -> choice.size().id())
            )
            .apply(instance, BogeyChoice::fromIds)
    );
    public static final StreamCodec<ByteBuf, BogeyChoice> STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, choice -> choice.style().id, ResourceLocation.STREAM_CODEC, choice -> choice.size().id(), BogeyChoice::fromIds
    );

    public static BogeyChoice of(UUID player) {
        return ModList.get().isLoaded("railways") ? RailwaysBogeyChoice.selected(player) : CREATE_STANDARD;
    }

    private static BogeyChoice fromIds(ResourceLocation styleId, ResourceLocation sizeId) {
        BogeyStyle style = (BogeyStyle)AllBogeyStyles.BOGEY_STYLES.get(styleId);
        BogeySize size = (BogeySize)BogeySizes.all().get(sizeId);
        return style != null && size != null && style.validSizes().contains(size) ? new BogeyChoice(style, size) : CREATE_STANDARD;
    }

    public boolean isStandard() {
        return this.style == AllBogeyStyles.STANDARD;
    }

    public AbstractBogeyBlock<?> block() {
        return this.style.getBlockForSize(this.size);
    }
}
