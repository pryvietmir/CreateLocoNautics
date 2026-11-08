package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.StrandHolder;
import com.lycoris.loconautics.common.strand.StrandTemplates;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ScrewLinkBlockEntity extends SmartBlockEntity implements StrandHolder {
    private static final Vec3 ANCHOR_NORTH = new Vec3(0.5, 0.75, 0.5);
    @Nullable
    private UUID strandId;

    public ScrewLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void tick() {
        super.tick();
        if (this.level != null && !this.level.isClientSide) {
            ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(this.level);
            if (strands != null && (this.strandId == null || strands.get(this.strandId) == null)) {
                SubLevel sub = Sable.HELPER.getContaining(this);
                ScrewLinkStrand strand = strands.create(new ScrewLinkStrand(UUID.randomUUID(), sub == null ? null : sub.getUniqueId(), this.getBlockPos()));
                this.setStrandId(strand.id);
            }
        }
    }

    @Nullable
    public UUID getStrandId() {
        return this.strandId;
    }

    public void setStrandId(@Nullable UUID id) {
        this.strandId = id;
        this.setChanged();
    }

    @Override
    public Vec3 getAttachmentPoint() {
        Direction facing = this.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
            ? (Direction)this.getBlockState().getValue(HorizontalDirectionalBlock.FACING)
            : Direction.NORTH;
        Vec3 local = rotateToFacing(ANCHOR_NORTH, facing);
        return Vec3.atLowerCornerOf(this.getBlockPos()).add(local);
    }

    public boolean decouple() {
        if (this.level != null && this.strandId != null) {
            ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(this.level);
            if (strands == null) {
                return false;
            } else {
                ScrewLinkStrand strand = strands.get(this.strandId);
                if (strand != null && strand.isCoupled()) {
                    strand.subB = null;
                    strand.localB = null;
                    strands.touch();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    private static Vec3 rotateToFacing(Vec3 north, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(1.0 - north.z, north.y, north.x);
            case SOUTH -> new Vec3(1.0 - north.x, north.y, 1.0 - north.z);
            case WEST -> new Vec3(north.z, north.y, 1.0 - north.x);
            default -> north;
        };
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.strandId != null) {
            tag.putUUID("Strand", this.strandId);
        }
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.strandId = !tag.hasUUID("Strand") || !clientPacket && StrandTemplates.isPlacing() ? null : tag.getUUID("Strand");
    }
}
