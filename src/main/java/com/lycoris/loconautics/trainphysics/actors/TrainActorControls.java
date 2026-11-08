package com.lycoris.loconautics.trainphysics.actors;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TrainActorControls {
    private static final Map<UUID, TrainActorControls.Snapshot> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, TrainActorControls.Snapshot> CLIENT = new ConcurrentHashMap<>();

    private TrainActorControls() {
    }

    public static boolean isDisabled(BlockEntity be) {
        if (be != null && be.getLevel() != null) {
            Level level = be.getLevel();
            BlockState state = be.getBlockState();
            if (!controllable(state)) {
                return false;
            }

            SubLevel sub = level.isClientSide ? Sable.HELPER.getContainingClient(be) : Sable.HELPER.getContaining(be);
            return disabledIn(level, sub, state);
        } else {
            return false;
        }
    }

    public static boolean isDisabled(Level level, BlockPos pos, BlockState state) {
        if (level != null && pos != null && state != null && controllable(state)) {
            SubLevel sub = level.isClientSide ? Sable.HELPER.getContainingClient(pos) : Sable.HELPER.getContaining(level, pos);
            return disabledIn(level, sub, state);
        } else {
            return false;
        }
    }

    public static boolean onTrain(Level level, BlockPos pos) {
        if (level != null && pos != null) {
            SubLevel sub = level.isClientSide ? Sable.HELPER.getContainingClient(pos) : Sable.HELPER.getContaining(level, pos);
            return sub != null && !sub.isRemoved() && sub.getUniqueId() != null ? isTrain(level, sub.getUniqueId()) : false;
        } else {
            return false;
        }
    }

    public static void clearServer() {
        SERVER.clear();
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    private static boolean controllable(BlockState state) {
        return AllItemTags.CONTRAPTION_CONTROLLED.matches(state.getBlock().asItem());
    }

    private static boolean disabledIn(Level level, SubLevel sub, BlockState state) {
        if (sub != null && !sub.isRemoved()) {
            UUID id = sub.getUniqueId();
            if (id != null && isTrain(level, id)) {
                List<ItemStack> filters = filters(level, sub, id);
                if (filters.isEmpty()) {
                    return false;
                }

                ItemStack machine = new ItemStack(state.getBlock());

                for (ItemStack filter : filters) {
                    if (filter.isEmpty() || ContraptionControlsMovement.isSameFilter(filter, machine)) {
                        return true;
                    }
                }

                return false;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static boolean isTrain(Level level, UUID subLevelId) {
        return level.isClientSide ? SableTrainClientRegistry.isTrain(subLevelId) : SableTrainRegistry.bySubLevel(subLevelId) != null;
    }

    private static List<ItemStack> filters(Level level, SubLevel sub, UUID id) {
        Map<UUID, TrainActorControls.Snapshot> cache = level.isClientSide ? CLIENT : SERVER;
        long now = level.getGameTime();
        TrainActorControls.Snapshot cached = cache.get(id);
        if (cached != null && cached.gameTime() == now) {
            return cached.filters();
        }

        List<ItemStack> found = new ArrayList<>();

        for (ContraptionControlsBlockEntity controls : SubLevelBlocks.findAll(sub, ContraptionControlsBlockEntity.class)) {
            if (controls.disabled) {
                found.add(controls.filtering.getFilter());
            }
        }

        cache.put(id, new TrainActorControls.Snapshot(now, found));
        return found;
    }

    private record Snapshot(long gameTime, List<ItemStack> filters) {
    }
}
