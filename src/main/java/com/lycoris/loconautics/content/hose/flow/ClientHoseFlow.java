package com.lycoris.loconautics.content.hose.flow;

import com.lycoris.loconautics.content.hose.HoseSwell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class ClientHoseFlow {
    private static volatile Map<UUID, Boolean> flowing = Map.of();

    private ClientHoseFlow() {
    }

    public static void accept(List<UUID> forward, List<UUID> backward) {
        Map<UUID, Boolean> next = new HashMap<>();
        forward.forEach(id -> next.put(id, true));
        backward.forEach(id -> next.put(id, false));
        flowing = next;
    }

    public static float swell(UUID strandId, double cumDist, double total, float partialTick) {
        Boolean forward = flowing.get(strandId);
        if (forward != null && Minecraft.getInstance().level != null) {
            double ticks = (float)Minecraft.getInstance().level.getGameTime() + partialTick;
            return HoseSwell.travelling(ticks, forward ? cumDist : total - cumDist);
        } else {
            return 1.0F;
        }
    }
}
