package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TurntablePlateBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {
    @Nullable
    private BlockPos parent;
    @Nullable
    private UUID parentSubLevelId;
    private boolean assembling;

    public TurntablePlateBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    public void beforeAssembly() {
        this.assembling = true;
    }

    public void remove() {
        if (this.level != null && !this.level.isClientSide && !this.assembling) {
            this.destroyBearing();
        }

        super.remove();
    }

    private void destroyBearing() {
        if (this.parent != null && this.level.getBlockState(this.parent).is((Block)LoconauticsRegistries.TURNTABLE_BEARING.get())) {
            this.level.destroyBlock(this.parent, false);
        }
    }

    public void setParent(TurntableBearingBlockEntity be) {
        SubLevel subLevel = Sable.HELPER.getContaining(be);
        this.parent = be.getBlockPos();
        this.parentSubLevelId = subLevel != null ? subLevel.getUniqueId() : null;
    }

    public float propagateRotationTo(
        KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs
    ) {
        return this.parent != null && target.equals(this.level.getBlockEntity(this.parent))
            ? 1.0F
            : super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return this.parent != null && other.equals(this.level.getBlockEntity(this.parent));
    }

    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (this.parent != null) {
            neighbours.add(this.parent);
        }

        return super.addPropagationLocations(block, state, neighbours);
    }

    protected void write(CompoundTag compound, Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (this.parent != null) {
            compound.put("ParentPos", NbtUtils.writeBlockPos(this.parent));
        }

        if (this.parentSubLevelId != null) {
            compound.putUUID("ParentSubLevelId", this.parentSubLevelId);
        }
    }

    protected void read(CompoundTag compound, Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("ParentPos")) {
            this.parent = (BlockPos)NbtUtils.readBlockPos(compound, "ParentPos").orElse(null);
        }

        if (compound.hasUUID("ParentSubLevelId")) {
            this.parentSubLevelId = compound.getUUID("ParentSubLevelId");
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (this.parent != null && this.level.getBlockEntity(this.parent) instanceof TurntableBearingBlockEntity bearing) {
            bearing.updateServoCoefficients();
        }
    }

    @Nullable
    @Override
    public Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        if (this.parent != null && this.parentSubLevelId != null) {
            SubLevel subLevel = SubLevelContainer.getContainer(this.level).getSubLevel(this.parentSubLevelId);
            return subLevel != null ? List.of(subLevel) : null;
        } else {
            return null;
        }
    }

    public void setParentAssembleNextTick() {
        if (this.parent != null && this.level.getBlockEntity(this.parent) instanceof TurntableBearingBlockEntity bearing) {
            bearing.assembleNextTick = true;
        }
    }

    public void fixParentLinkingWhenMoved() {
        if (!this.level.isClientSide() && this.parent != null) {
            if (this.level.getBlockEntity(this.parent) instanceof TurntableBearingBlockEntity bearing) {
                bearing.setPlatePos(this.getBlockPos());
                TurntablePlateBlockEntity var5 = this;
                ServerSubLevel newSubLevel = (ServerSubLevel)Sable.HELPER.getContaining(var5);
                if (newSubLevel == null) {
                    bearing.setSubLevelID(null);
                    bearing.reattachConstraint(null, true);
                } else {
                    UUID newId = newSubLevel.getUniqueId();
                    if (!newId.equals(bearing.getSubLevelID())) {
                        bearing.setSubLevelID(newId);
                        bearing.reattachConstraint(newSubLevel, true);
                    }
                }
            }
        }
    }
}
