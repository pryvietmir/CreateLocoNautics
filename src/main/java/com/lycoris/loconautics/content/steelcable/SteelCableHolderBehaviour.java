package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.StrandAttachmentStore;
import com.lycoris.loconautics.common.strand.StrandTemplateState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;

public class SteelCableHolderBehaviour extends BlockEntityBehaviour implements StrandAttachmentStore {
    public static final BehaviourType<SteelCableHolderBehaviour> TYPE = new BehaviourType<>();
    private final List<UUID> attached = new ArrayList<>();
    private final int capacity;
    private final StrandTemplateState templateState = new StrandTemplateState(SteelCableTemplateKind.INSTANCE);

    public SteelCableHolderBehaviour(SmartBlockEntity be, int capacity) {
        super(be);
        this.capacity = Math.max(1, capacity);
    }

    public BehaviourType<?> getType() {
        return TYPE;
    }

    public List<UUID> attachedStrandIds() {
        return this.attached;
    }

    @Override
    public void attach(UUID id) {
        if (!this.attached.contains(id)) {
            this.attached.add(id);
            this.blockEntity.setChanged();
        }
    }

    @Override
    public void detach(UUID id) {
        if (this.attached.remove(id)) {
            this.blockEntity.setChanged();
        }
    }

    @Override
    public boolean holds(UUID id) {
        this.pruneDead();
        return this.attached.contains(id);
    }

    @Override
    public boolean isFree() {
        this.pruneDead();
        return this.attached.size() < this.capacity;
    }

    private void pruneDead() {
        if (!this.attached.isEmpty() && this.getWorld() != null) {
            SteelCableStrandManager mgr = SteelCableStrandManager.get(this.getWorld());
            if (mgr != null) {
                if (this.attached.removeIf(id -> mgr.get(id) == null)) {
                    this.blockEntity.setChanged();
                }
            }
        }
    }

    public void write(CompoundTag nbt, Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        ListTag list = new ListTag();

        for (UUID id : this.attached) {
            list.add(NbtUtils.createUUID(id));
        }

        nbt.put("AttachedStrands", list);
        this.templateState.write(nbt, this.blockEntity, this.attached);
    }

    public void tick() {
        super.tick();
        this.templateState.tick(this.blockEntity);
    }

    public StrandTemplateState templateState() {
        return this.templateState;
    }

    public void read(CompoundTag nbt, Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        this.attached.clear();
        ListTag list = nbt.getList("AttachedStrands", 11);

        for (int i = 0; i < list.size(); i++) {
            this.attached.add(NbtUtils.loadUUID(list.get(i)));
        }

        if (this.templateState.read(nbt, clientPacket)) {
            this.attached.clear();
        }
    }
}
