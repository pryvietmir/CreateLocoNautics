package com.lycoris.loconautics.content.ball;

import com.lycoris.loconautics.common.grab.GrabConstraint;
import com.lycoris.loconautics.common.grab.GrabbableActor;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniondc;

public class BallBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, GrabbableActor {
    private final Map<UUID, GrabConstraint> grabbers = new HashMap<>();

    public BallBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void updateGrab(UUID player, float range, boolean rotating, Quaterniondc orientation) {
        GrabConstraint c = this.grabbers.get(player);
        if (c == null) {
            c = new GrabConstraint(player, range);
            this.grabbers.put(player, c);
        } else {
            c.setScrollDistance(range);
        }

        c.setRotation(rotating, orientation);
    }

    @Override
    public Map<UUID, GrabConstraint> grabbers() {
        return this.grabbers;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (subLevel.getLevel() instanceof ServerLevel level) {
            this.driveGrabs(subLevel, level);
        }
    }

    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        return List.of();
    }
}
