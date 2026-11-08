package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.hose.link.HoseLinks;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRope;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class SteelCableItem extends Item {
    public SteelCableItem(Properties properties) {
        super(properties);
    }

    public static DataComponentType<BlockPos> firstConnection() {
        return (DataComponentType<BlockPos>)LoconauticsRegistries.STEEL_CABLE_FIRST_CONNECTION.get();
    }

    public SteelCableType cableType() {
        return SteelCableType.DEFAULT;
    }

    public double maxRange() {
        return (Double)LoconauticsConfig.STEEL_CABLE_MAX_RANGE.get();
    }

    public boolean isValidAttachment(Level level, BlockPos pos) {
        SteelCableHolder holder = getHolder(level, pos);
        return holder != null && holder.accepts(this.cableType()) && holder.canAttachStrand();
    }

    @Nullable
    public static SteelCableHolder getHolder(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SteelCableHolder holder ? holder : null;
    }

    public InteractionResult useOn(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            stack.remove(firstConnection());
            return InteractionResult.SUCCESS;
        }

        if (this.isValidAttachment(level, clickedPos)) {
            if (stack.has(firstConnection())) {
                if (!level.isClientSide) {
                    BlockPos firstPos = (BlockPos)stack.get(firstConnection());
                    if (!this.createSteelCable((ServerLevel)level, firstPos, clickedPos)) {
                        stack.remove(firstConnection());
                        return InteractionResult.SUCCESS;
                    }
                }

                stack.remove(firstConnection());
                if (player != null && !player.isCreative()) {
                    stack.shrink(1);
                }

                return InteractionResult.SUCCESS;
            } else {
                stack.set(firstConnection(), clickedPos);
                return InteractionResult.SUCCESS;
            }
        } else {
            return super.useOn(context);
        }
    }

    public boolean createSteelCable(ServerLevel level, BlockPos posA, BlockPos posB) {
        SteelCableHolder holderA = getHolder(level, posA);
        SteelCableHolder holderB = getHolder(level, posB);
        BlockPos clickedA = posA;
        BlockPos clickedB = posB;
        if (holderA != null && holderB != null && holderA != holderB && holderA.accepts(this.cableType()) && holderB.accepts(this.cableType())) {
            if (holderB.isWinch() && !holderA.isWinch()) {
                SteelCableHolder tmpHolder = holderA;
                holderA = holderB;
                holderB = tmpHolder;
                BlockPos tmpPos = clickedA;
                clickedA = clickedB;
                clickedB = tmpPos;
            }

            if (holderA.isWinch() && holderB.isWinch()) {
                return false;
            }

            if (holderA.canAttachStrand() && holderB.canAttachStrand()) {
                if (!this.cableType().carriesFluid() || !HoseLinks.atLinkLimit((BlockEntity)holderA) && !HoseLinks.atLinkLimit((BlockEntity)holderB)) {
                    Vec3 localPointA = holderA.getAttachmentPoint();
                    Vec3 localPointB = holderB.getAttachmentPoint();
                    SubLevel subLevelA = Sable.HELPER.getContaining(level, localPointA);
                    SubLevel subLevelB = Sable.HELPER.getContaining(level, localPointB);
                    Vec3 worldA = Sable.HELPER.projectOutOfSubLevel(level, localPointA);
                    Vec3 worldB = Sable.HELPER.projectOutOfSubLevel(level, localPointB);
                    if (!worldB.closerThan(worldA, this.maxRange())) {
                        this.logRefusal("range", holderA, holderB);
                        return false;
                    }

                    UUID id = UUID.randomUUID();
                    double restLength = worldA.distanceTo(worldB);
                    SteelCableStrand strand = new SteelCableStrand(
                        id,
                        subLevelA != null ? subLevelA.getUniqueId() : null,
                        clickedA,
                        subLevelB != null ? subLevelB.getUniqueId() : null,
                        clickedB,
                        this.cableType()
                    );
                    strand.restLength = restLength;
                    SteelCableRope rope = SteelCableRope.build(
                        id,
                        strand.type,
                        restLength,
                        strand.subA,
                        strand.localA,
                        new Vector3d(worldA.x, worldA.y, worldA.z),
                        strand.subB,
                        strand.localB,
                        new Vector3d(worldB.x, worldB.y, worldB.z)
                    );
                    strand.points = new ArrayList<>(rope.getPoints());
                    SteelCableStrandManager strandsMgr = SteelCableStrandManager.get(level);
                    SteelCableRopeManager ropesMgr = SteelCableRopeManager.get(level);
                    if (strandsMgr != null && ropesMgr != null) {
                        strandsMgr.create(strand);
                        ropesMgr.add(rope);
                        holderA.onStrandAttached(strand);
                        holderB.onStrandAttached(strand);
                        if (holderA instanceof SmartBlockEntity sbeA) {
                            sbeA.notifyUpdate();
                        }

                        if (holderB instanceof SmartBlockEntity sbeB) {
                            sbeB.notifyUpdate();
                        }

                        level.playSound(null, posA, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
                        level.playSound(null, posB, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    this.logRefusal("link limit", holderA, holderB);
                    return false;
                }
            } else {
                this.logRefusal("slot taken", holderA, holderB);
                return false;
            }
        } else {
            this.logRefusal("holders", holderA, holderB);
            return false;
        }
    }

    private void logRefusal(String why, @Nullable SteelCableHolder a, @Nullable SteelCableHolder b) {
        if (this.cableType().carriesFluid()) {
        }
    }
}
