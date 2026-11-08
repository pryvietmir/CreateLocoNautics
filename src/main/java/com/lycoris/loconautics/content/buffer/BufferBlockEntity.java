package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatMaterialHolder;
import com.lycoris.loconautics.common.copycat.CopycatMaterialStore;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class BufferBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, CopycatMaterialHolder {
    private final CopycatMaterialStore copycat = new CopycatMaterialStore(this);
    @Nullable
    private transient UUID contactSubLevelId;
    @Nullable
    private transient BlockPos contactLocalPos;
    private transient volatile double pushDepth;
    private transient double smoothDepth;
    private transient int contactMisses;
    private float compressionPixels;
    private transient float shown = Float.NaN;
    private transient double shownClock = Double.NaN;

    public BufferBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public CopycatMaterialStore copycat() {
        return this.copycat;
    }

    public ModelData getModelData() {
        return CopycatBlockLogic.modelData(this.copycat);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(this.getBlockPos()).inflate(2.0);
    }

    public Direction facing() {
        return (Direction)this.getBlockState().getValue(BufferBlock.FACING);
    }

    public BufferSize size() {
        return (BufferSize)this.getBlockState().getValue(BufferSize.SIZE);
    }

    public Vector3d tipLocal() {
        Direction facing = this.facing();
        double out = this.size().extensionBlocks();
        return new Vector3d(
            this.getBlockPos().getX() + 0.5 + facing.getStepX() * out,
            this.getBlockPos().getY() + 0.625 + ((CopycatPlacement)this.getBlockState().getValue(CopycatPlacement.PLACEMENT)).blocks(),
            this.getBlockPos().getZ() + 0.5 + facing.getStepZ() * out
        );
    }

    public float shownExtensionPixels(float partialTicks) {
        float target = this.size().extensionPixels() - this.compressionPixels;
        if (this.level != null && !Float.isNaN(this.shown)) {
            double now = (float)this.level.getGameTime() + partialTicks;
            double dt = Double.isNaN(this.shownClock) ? 0.0 : Math.max(0.0, now - this.shownClock);
            this.shownClock = now;
            this.shown = this.shown + (target - this.shown) * (float)(1.0 - Math.exp(-dt * 0.35F * 4.0));
        } else {
            this.shown = target;
        }

        return Math.max(0.0F, this.shown);
    }

    void setContact(@Nullable UUID subLevelId, @Nullable BlockPos localPos) {
        if (subLevelId == null) {
            if (this.contactSubLevelId != null && ++this.contactMisses < 5) {
                return;
            }

            this.pushDepth = 0.0;
        }

        this.contactMisses = 0;
        this.contactSubLevelId = subLevelId;
        this.contactLocalPos = localPos;
    }

    @Nullable
    UUID contactSubLevelId() {
        return this.contactSubLevelId;
    }

    @Nullable
    BlockPos contactLocalPos() {
        return this.contactLocalPos;
    }

    void syncCompression() {
        double target = Math.min(this.pushDepth * 0.5, this.size().extensionBlocks());
        this.smoothDepth = this.smoothDepth + (target - this.smoothDepth) * (target > this.smoothDepth ? 0.5 : 0.12);
        if (this.smoothDepth < 0.0078125) {
            this.smoothDepth = 0.0;
        }

        float quantized = (float)(Math.round(this.smoothDepth / 0.015625) * 0.015625 * 16.0);
        if (quantized != this.compressionPixels) {
            this.compressionPixels = quantized;
            this.setChanged();
            this.sendData();
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        UUID partnerId = this.contactSubLevelId;
        BlockPos partnerPos = this.contactLocalPos;
        if (partnerId != null && partnerPos != null) {
            if (subLevel.getUniqueId().compareTo(partnerId) < 0) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
                if (container != null
                    && container.getSubLevel(partnerId) instanceof ServerSubLevel partnerSub
                    && !partnerSub.isRemoved()
                    && partnerSub != subLevel
                    && partnerSub.getLevel().getBlockEntity(partnerPos) instanceof BufferBlockEntity partnerBe) {
                    RigidBodyHandle partnerHandle = RigidBodyHandle.of(partnerSub);
                    if (partnerHandle != null && partnerHandle.isValid()) {
                        double depth = BufferSpring.pressPair(subLevel, handle, this, partnerSub, partnerHandle, partnerBe, timeStep);
                        this.pushDepth = depth;
                        partnerBe.pushDepth = depth;
                        if (depth > 0.0 && !BufferSpring.held(partnerSub)) {
                            container.physicsSystem().getPipeline().wakeUp(partnerSub);
                        }
                    } else {
                        this.pushDepth = 0.0;
                    }
                } else {
                    this.pushDepth = 0.0;
                }
            }
        } else {
            this.pushDepth = BufferSpring.pressWorld(subLevel, handle, this, timeStep);
        }
    }

    static Vector3d worldTip(ServerSubLevel sub, BufferBlockEntity be) {
        return sub.logicalPose().transformPosition(be.tipLocal());
    }

    static Vector3d worldOutward(ServerSubLevel sub, BufferBlockEntity be) {
        Direction facing = be.facing();
        Vector3d out = new Vector3d(facing.getStepX(), 0.0, facing.getStepZ());
        return sub.logicalPose().transformNormal(out, out).normalize();
    }

    static Vector3d localOf(ServerSubLevel sub, Vector3dc worldDirection) {
        return new Quaterniond(sub.logicalPose().orientation()).conjugate().transform(new Vector3d(worldDirection));
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Compression", this.compressionPixels);
        this.copycat.write(tag, registries);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.compressionPixels = tag.getFloat("Compression");
        this.copycat.read(tag, registries, clientPacket);
    }
}
