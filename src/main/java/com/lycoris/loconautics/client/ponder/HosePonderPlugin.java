package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class HosePonderPlugin implements PonderPlugin {
    public String getModId() {
        return "loconautics";
    }

    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(new ResourceLocation[]{LoconauticsRegistries.HOSE.getId()})
            .addStoryBoard("hose/hose", HoseScenes::connect)
            .addStoryBoard("hose/hose", HoseScenes::nozzle)
            .addStoryBoard("hose/hose", HoseScenes::fluid)
            .addStoryBoard("hose/hose", HoseScenes::spray);
        helper.forComponents(new ResourceLocation[]{LoconauticsRegistries.HOSE_WINCH.getId()})
            .addStoryBoard("hose/hose", HoseScenes::winch)
            .addStoryBoard("hose/hose", HoseScenes::connect)
            .addStoryBoard("hose/hose", HoseScenes::fluid);
        helper.forComponents(new ResourceLocation[]{LoconauticsRegistries.HOSE_NOZZLE.getId()})
            .addStoryBoard("hose/hose", HoseScenes::connect)
            .addStoryBoard("hose/hose", HoseScenes::nozzle)
            .addStoryBoard("hose/hose", HoseScenes::fluid)
            .addStoryBoard("hose/hose", HoseScenes::spray);
    }
}
