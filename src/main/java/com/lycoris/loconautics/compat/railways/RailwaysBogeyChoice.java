package com.lycoris.loconautics.compat.railways;

import com.lycoris.loconautics.content.casingpicker.BogeyChoice;
import com.lycoris.loconautics.mixin.compat.railways.BogeyMenuHandlerServerAccessor;
import com.railwayteam.railways.registry.CRBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.bogey.BogeySizes.BogeySize;
import com.simibubi.create.content.trains.track.TrackMaterial.TrackType;
import java.util.Optional;
import java.util.UUID;
import net.createmod.catnip.data.Pair;

public final class RailwaysBogeyChoice {
    private RailwaysBogeyChoice() {
    }

    public static BogeyChoice fitTo(BogeyChoice choice, TrackType trackType) {
        Optional<BogeyStyle> mapped = CRBogeyStyles.getMapped(choice.style(), trackType, true);
        if (!mapped.isEmpty() && mapped.get() != choice.style()) {
            BogeyStyle style = mapped.get();
            BogeySize size = style.validSizes().contains(choice.size()) ? choice.size() : (BogeySize)style.validSizes().iterator().next();
            return new BogeyChoice(style, size);
        } else {
            return choice;
        }
    }

    public static BogeyChoice selected(UUID player) {
        Pair<BogeyStyle, BogeySize> picked = BogeyMenuHandlerServerAccessor.loconautics$getStyle(player);
        BogeyStyle style = (BogeyStyle)picked.getFirst();
        BogeySize size = (BogeySize)picked.getSecond();
        if (size == null || !style.validSizes().contains(size)) {
            size = (BogeySize)style.validSizes().iterator().next();
        }

        return new BogeyChoice(style, size);
    }
}
