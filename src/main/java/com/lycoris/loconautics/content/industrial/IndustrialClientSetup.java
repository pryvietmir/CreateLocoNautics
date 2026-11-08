package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.CreateClient;
import java.util.function.Function;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.Block;

public final class IndustrialClientSetup {
    private IndustrialClientSetup() {
    }

    public static void register() {
        for (Block block : new Block[]{(Block)IndustrialBlocks.VALVE.get(), (Block)IndustrialBlocks.SMART_PIPE.get(), (Block)IndustrialBlocks.GLASS_PIPE.get()}) {
            ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutoutMipped());
        }

        for (Block block : new Block[]{
            (Block)IndustrialBlocks.PIPE.get(),
            (Block)IndustrialBlocks.PUMP.get(),
            (Block)IndustrialBlocks.SMART_PIPE.get(),
            (Block)IndustrialBlocks.VALVE.get(),
            (Block)IndustrialBlocks.GLASS_PIPE.get()
        }) {
            swap(block, IndustrialPipeAttachmentModel::withAO);
        }

        swap((Block)IndustrialBlocks.TANK.get(), IndustrialTankModel::new);
    }

    private static void swap(Block block, Function<BakedModel, BakedModel> factory) {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels().register(RegisteredObjectsHelper.getKeyOrThrow(block), factory::apply);
    }
}
