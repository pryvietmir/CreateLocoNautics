package com.lycoris.loconautics.mixin.create.bogey;

import com.lycoris.loconautics.content.bearingaxle.BogeyShaft;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractBogeyBlock.class, remap = false)
public abstract class BogeyShaftedStateMixin extends Block {
    protected BogeyShaftedStateMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "createBlockStateDefinition", at = @At("HEAD"))
    private void loconautics$addShafted(Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(new Property[]{BogeyShaft.SHAFTED});
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void loconautics$defaultNotShafted(CallbackInfo ci) {
        this.registerDefaultState((BlockState)(Object)this.defaultBlockState().setValue(BogeyShaft.SHAFTED, false));
    }

    @Inject(method = "propertiesToCopy", at = @At("RETURN"), cancellable = true)
    private void loconautics$copyShafted(CallbackInfoReturnable<List<Property<?>>> cir) {
        List<Property<?>> copied = new ArrayList<>((Collection<? extends Property<?>>)cir.getReturnValue());
        copied.add(BogeyShaft.SHAFTED);
        cir.setReturnValue(copied);
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (BogeyShaft.isShafted(state) && !newState.is(this)) {
            BogeyShaft.relink(level, pos, (Axis)state.getValue(BlockStateProperties.HORIZONTAL_AXIS));
        }
    }
}
