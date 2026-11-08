package com.lycoris.loconautics.content.boiler.barrel;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.foundation.block.connected.CTType;
import com.simibubi.create.foundation.block.connected.CTTypeRegistry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.CTContext;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.ContextRequirement;
import net.minecraft.resources.ResourceLocation;

public class BoilerCTType implements CTType {
    public static final BoilerCTType INSTANCE = new BoilerCTType();
    public static final int ROWS = 3;
    private static boolean registered;
    private final ResourceLocation id = LoconauticsConstants.id("boiler_barrel");

    public static void register() {
        if (!registered) {
            registered = true;
            CTTypeRegistry.register(INSTANCE);
        }
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public int getSheetSize() {
        return 1;
    }

    public ContextRequirement getContextRequirement() {
        return ContextRequirement.builder().horizontal().build();
    }

    public int getTextureIndex(CTContext context) {
        boolean r = context.right;
        boolean l = context.left;
        if (r && !l) {
            return 0;
        } else {
            return l && !r ? 2 : 1;
        }
    }
}
