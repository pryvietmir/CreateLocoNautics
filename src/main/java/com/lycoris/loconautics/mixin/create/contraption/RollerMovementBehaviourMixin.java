package com.lycoris.loconautics.mixin.create.contraption;

import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.item.ItemHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RollerMovementBehaviour.class, remap = false)
public abstract class RollerMovementBehaviourMixin {
    private static final int TUNNEL_PAVE_MODE = 0;

    @Shadow(remap = false)
    protected abstract BlockState getStateToPaveWith(MovementContext var1);

    @Shadow(remap = false)
    public abstract boolean canBreak(Level var1, BlockPos var2, BlockState var3);

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void loconautics$directPaveBlockedRoadbed(MovementContext context, BlockPos pos, CallbackInfo ci) {
        if (context != null && context.world != null && !context.world.isClientSide && context.blockEntityData != null) {
            if (context.world instanceof ServerLevel server && GhostWheelBodies.isWheelBodyAt(server, pos)) {
                ci.cancel();
            } else if (context.blockEntityData.getInt("ScrollValue") == 0 && context.contraption != null) {
                if (context.world.isLoaded(pos)) {
                    if (!isInsideSubLevel(context.world, context.contraption.anchor)) {
                        BlockState paveWith = this.getStateToPaveWith(context);
                        if (!paveWith.isAir()) {
                            BlockState existing = context.world.getBlockState(pos);
                            if (!existing.is(paveWith.getBlock()) && this.canClearForPaving(context, pos, existing)) {
                                if (consumePavingItem(context)) {
                                    context.world.setBlockAndUpdate(pos, paveWith);
                                }

                                ci.cancel();
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isInsideSubLevel(Level world, BlockPos anchor) {
        if (anchor == null) {
            return false;
        }

        SubLevelAccess access = SableCompanion.INSTANCE.getContaining(world, anchor.getX() >> 4, anchor.getZ() >> 4);
        return access != null;
    }

    private boolean canClearForPaving(MovementContext context, BlockPos pos, BlockState existing) {
        if (existing.isAir() || existing.is(BlockTags.PORTALS) || !existing.getFluidState().isEmpty()) {
            return false;
        } else {
            return !existing.canBeReplaced() && !existing.is(BlockTags.LEAVES) && !existing.getCollisionShape(context.world, pos).isEmpty()
                ? this.canBreak(context.world, pos, existing)
                : true;
        }
    }

    private static boolean consumePavingItem(MovementContext context) {
        if (context == null) {
            return false;
        }

        if (context.contraption != null && context.contraption.hasUniversalCreativeCrate) {
            return true;
        }

        if (context.contraption == null) {
            return false;
        }

        MountedStorageManager storage = context.contraption.getStorage();
        if (storage != null) {
            if (consumeFrom(context, storage.getAllItems())) {
                return true;
            } else {
                return consumeFrom(context, storage.getMountedItems()) ? true : consumeFrom(context, context.getItemStorage());
            }
        } else {
            return false;
        }
    }

    private static boolean consumeFrom(MovementContext context, IItemHandler inventory) {
        if (context != null && context.world != null && inventory != null) {
            FilterItemStack filter = context.getFilterFromBE();
            if (filter == null) {
                return false;
            }

            ItemStack extracted = ItemHelper.extract(inventory, stack -> filter.test(context.world, stack), 1, false);
            return !extracted.isEmpty();
        } else {
            return false;
        }
    }
}
