package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.ponder.CreatePonderPlugin;
import dev.simulated_team.simulated.ponder.scenes.RopeScenes;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class LoconauticsPonderPlugin extends CreatePonderPlugin {
    public String getModId() {
        return "simulated";
    }

    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(new ResourceLocation[]{LoconauticsRegistries.STEEL_CABLE.getId()})
            .addStoryBoard("rope", RopeScenes::ropeIntro)
            .addStoryBoard("rope", RopeScenes::ropeConnections);
    }

    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
    }
}
