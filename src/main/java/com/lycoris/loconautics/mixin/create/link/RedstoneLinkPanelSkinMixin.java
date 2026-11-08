package com.lycoris.loconautics.mixin.create.link;

import com.lycoris.loconautics.content.controls.panel.PanelLink;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RedstoneLinkBlock.class, remap = false)
public abstract class RedstoneLinkPanelSkinMixin extends Block {
    private RedstoneLinkPanelSkinMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void loconautics$defaultToVanillaSkin(Properties properties, CallbackInfo ci) {
        this.registerDefaultState((BlockState)this.defaultBlockState().setValue(PanelLink.SKIN, false));
    }

    @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
    private void loconautics$addSkin(Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(new Property[]{PanelLink.SKIN});
    }

    protected RenderShape getRenderShape(BlockState state) {
        return PanelLink.isPanel(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }
}
