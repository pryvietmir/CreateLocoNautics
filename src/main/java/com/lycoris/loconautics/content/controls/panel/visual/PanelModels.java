package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import dev.engine_room.flywheel.api.model.Model;

public final class PanelModels {
    private PanelModels() {
    }

    public static Model plate() {
        return ShiftedModels.plain(LoconauticsPartialModels.PLATE);
    }

    public static Model slimPlate() {
        return ShiftedModels.plain(LoconauticsPartialModels.SLIM_PLATE);
    }

    public static Model body(int body) {
        return ShiftedModels.shifted(LoconauticsPartialModels.BODY, LoconauticsSpriteShifts.BODY, 14 * ((body + 1) % 4), 0);
    }

    public static Model bigBody(int body) {
        return ShiftedModels.shifted(LoconauticsPartialModels.BIG_BODY, LoconauticsSpriteShifts.BIG_BODY, 14 * ((body + 1) % 4), 0);
    }

    public static Model slimBody(int body) {
        return ShiftedModels.shifted(LoconauticsPartialModels.SLIM_BODY, LoconauticsSpriteShifts.SLIM_BODY, 14 * ((body + 1) % 4), 0);
    }
}
