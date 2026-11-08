package com.lycoris.loconautics.common.strand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class StrandTemplateState {
    private static final String RECORDS = "StrandRecords";
    private static final String PENDING = "StrandPending";
    private static final int GIVE_UP_TICKS = 200;
    private final StrandTemplateKind kind;
    private final List<CompoundTag> records = new ArrayList<>();
    private final List<CompoundTag> pending = new ArrayList<>();
    private int waited;

    public StrandTemplateState(StrandTemplateKind kind) {
        this.kind = kind;
    }

    public void write(CompoundTag nbt, BlockEntity be, Collection<UUID> ids) {
        List<CompoundTag> live = new ArrayList<>();
        if (be.getLevel() instanceof ServerLevel level) {
            for (UUID id : ids) {
                CompoundTag record = this.kind.snapshot(level, be, id);
                if (record != null) {
                    live.add(record);
                }
            }
        } else {
            live.addAll(this.records);
        }

        putList(nbt, "StrandRecords", live);
        putList(nbt, "StrandPending", this.pending);
    }

    public boolean read(CompoundTag nbt, boolean clientPacket) {
        this.records.clear();
        this.pending.clear();
        readList(nbt, "StrandRecords", this.records);
        readList(nbt, "StrandPending", this.pending);
        if (!clientPacket && StrandTemplates.isPlacing()) {
            for (CompoundTag record : this.records) {
                if (!this.hasPending(record.getUUID("Id"))) {
                    this.pending.add(record);
                }
            }

            this.records.clear();
            this.waited = 0;
            return true;
        } else {
            return false;
        }
    }

    public boolean hasPending(UUID id) {
        return this.pending.stream().anyMatch(record -> record.getUUID("Id").equals(id));
    }

    public void dropPending(UUID id) {
        this.pending.removeIf(record -> record.getUUID("Id").equals(id));
    }

    public void tick(BlockEntity be) {
        if (!this.pending.isEmpty() && be.getLevel() instanceof ServerLevel level) {
            boolean var8 = false;

            for (CompoundTag record : new ArrayList<>(this.pending)) {
                BlockEntity far = this.findFar(level, be, record);
                if (far == null) {
                    var8 = true;
                } else {
                    this.pending.remove(record);
                    StrandTemplateState farState = this.kind.stateOf(far);
                    if (farState != null) {
                        farState.dropPending(record.getUUID("Id"));
                    }

                    this.kind.restore(level, be, far, record);
                }
            }

            this.waited = var8 ? this.waited + 1 : 0;
            if (this.waited > 200) {
                this.pending.clear();
                this.waited = 0;
            }

            be.setChanged();
        }
    }

    @Nullable
    private BlockEntity findFar(ServerLevel level, BlockEntity be, CompoundTag record) {
        int[] far = record.getIntArray("Far");
        if (far.length != 3) {
            return null;
        }

        int x = far[0];
        int z = far[2];
        int[][] flips = new int[][]{{x, z}, {-z, x}, {-x, -z}, {z, -x}, {-x, z}, {x, -z}, {z, x}, {-z, -x}};
        UUID id = record.getUUID("Id");
        String farBlock = record.getString("FarBlock");

        for (int[] flip : flips) {
            BlockPos pos = be.getBlockPos().offset(flip[0], far[1], flip[1]);
            BlockEntity candidate = level.getBlockEntity(pos);
            if (candidate != null && candidate != be && farBlock.equals(BuiltInRegistries.BLOCK.getKey(candidate.getBlockState().getBlock()).toString())) {
                StrandTemplateState state = this.kind.stateOf(candidate);
                if (state == null ? this.kind.farMayLackRecord() : state.hasPending(id)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static void putList(CompoundTag nbt, String key, List<CompoundTag> list) {
        if (!list.isEmpty()) {
            ListTag tag = new ListTag();

            for (CompoundTag record : list) {
                tag.add(record.copy());
            }

            nbt.put(key, tag);
        }
    }

    private static void readList(CompoundTag nbt, String key, List<CompoundTag> out) {
        ListTag tag = nbt.getList(key, 10);

        for (int i = 0; i < tag.size(); i++) {
            out.add(tag.getCompound(i));
        }
    }
}
