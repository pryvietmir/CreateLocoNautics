package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.common.strand.SingleStrandAttachment;
import com.lycoris.loconautics.common.strand.StrandTemplateState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public class HoseHolderBehaviour extends BlockEntityBehaviour implements SingleStrandAttachment {
    public static final BehaviourType<HoseHolderBehaviour> TYPE = new BehaviourType<>();
    public final boolean isOwner;
    @Nullable
    private UUID attachedStrandId;
    private final StrandTemplateState templateState = new StrandTemplateState(HoseTemplateKind.INSTANCE);

    public HoseHolderBehaviour(SmartBlockEntity be, boolean isOwner) {
        super(be);
        this.isOwner = isOwner;
    }

    public BehaviourType<?> getType() {
        return TYPE;
    }

    public boolean isFree() {
        if (this.attachedStrandId == null) {
            return true;
        } else if (this.getWorld() != null && !this.getWorld().isClientSide && this.getAttachedStrand() == null) {
            this.attachedStrandId = null;
            this.blockEntity.setChanged();
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    public UUID getAttachedStrandId() {
        return this.attachedStrandId;
    }

    @Nullable
    @Override
    public UUID rawAttachedStrandId() {
        return this.attachedStrandId;
    }

    @Override
    public void rawAttachedStrandId(@Nullable UUID id) {
        this.attachedStrandId = id;
    }

    @Override
    public void markChanged() {
        this.blockEntity.setChanged();
    }

    @Nullable
    public HoseStrand getAttachedStrand() {
        if (this.attachedStrandId != null && this.getWorld() != null) {
            HoseStrandManager mgr = HoseStrandManager.get(this.getWorld());
            return mgr == null ? null : mgr.get(this.attachedStrandId);
        } else {
            return null;
        }
    }

    public void write(CompoundTag nbt, Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        this.writeAttachedStrand(nbt);
        this.templateState.write(nbt, this.blockEntity, this.attachedStrandId == null ? List.of() : List.of(this.attachedStrandId));
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
        this.readAttachedStrand(nbt);
        if (this.templateState.read(nbt, clientPacket)) {
            this.attachedStrandId = null;
        }
    }
}
