package com.lycoris.loconautics.content.hose.spray.entity;

import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteraction;
import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public class HoseSprayEntity extends Entity {
    private static final double GRAVITY = 0.013999999999999999;
    private static final double DRAG = 0.9;
    private static final int SAFETY_MAX_AGE = 60;
    private static final double ARM_DISTANCE_SQ = 1.0;
    private static final double SWEEP_STEP = 0.25;
    private static final double HIT_MARGIN = 0.3;
    private FluidStack fluid = FluidStack.EMPTY;
    private float power = 0.5F;
    private int age;
    private final Set<UUID> ignored = new HashSet<>();
    @Nullable
    private Vec3 origin;
    @Nullable
    private UUID attacker;

    public HoseSprayEntity(EntityType<? extends HoseSprayEntity> type, Level level) {
        super(type, level);
    }

    public static HoseSprayEntity launch(ServerLevel level, Vec3 origin, Vec3 dir, FluidStack fluid, float power, @Nullable UUID attacker) {
        HoseSprayEntity e = new HoseSprayEntity((EntityType<? extends HoseSprayEntity>)HoseSprayEntities.HOSE_SPRAY.get(), level);
        e.setPos(origin.x, origin.y, origin.z);
        e.origin = origin;
        e.attacker = attacker;
        double speed = 0.3 + 0.3 * power;
        Vec3 d = dir.lengthSqr() < 1.0E-9 ? new Vec3(0.0, -1.0, 0.0) : dir.normalize();
        e.setDeltaMovement(d.scale(speed));
        e.fluid = fluid.copy();
        e.power = power;
        level.addFreshEntity(e);
        return e;
    }

    public boolean isPushedByFluid() {
        return false;
    }

    public boolean displayFireAnimation() {
        return false;
    }

    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (++this.age <= 60 && !this.fluid.isEmpty() && !(this.getY() < this.level().getMinBuildHeight())) {
                HoseSprayInteraction interaction = HoseSprayInteractions.get(this.fluid);
                if (interaction == null) {
                    this.discard();
                } else {
                    ServerLevel slevel = (ServerLevel)this.level();
                    if (this.age == 1) {
                        for (Entity e : slevel.getEntities(this, this.getBoundingBox().inflate(Math.sqrt(1.0)), HoseSprayEntity::canBeSprayed)) {
                            this.ignored.add(e.getUUID());
                        }
                    }

                    Vec3 from = this.position();
                    Vec3 v = this.getDeltaMovement().add(0.0, -0.013999999999999999, 0.0).scale(0.9);
                    this.setDeltaMovement(v);
                    Vec3 to = from.add(v);
                    boolean hitEntity = this.hitEntitiesAlong(slevel, interaction, from, to);
                    if (this.sweepBlocks(slevel, interaction, from, to, v) == null && !hitEntity) {
                        this.setPos(to.x, to.y, to.z);
                    } else {
                        this.discard();
                    }
                }
            } else {
                this.discard();
            }
        }
    }

    @Nullable
    private Vec3 sweepBlocks(ServerLevel slevel, HoseSprayInteraction interaction, Vec3 from, Vec3 to, Vec3 v) {
        int steps = Math.max(1, (int)Math.ceil(v.length() / 0.25));
        BlockPos last = BlockPos.containing(from);

        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(to, (double)i / steps);
            BlockPos cell = BlockPos.containing(p);
            if (!cell.equals(last)) {
                Direction dir = stepDir(last, cell, v);
                last = cell;
                boolean armed = this.origin == null || p.distanceToSqr(this.origin) > 1.0;
                if (interaction.onBlock(slevel, cell, slevel.getBlockState(cell), dir, this.fluid, this.power) && armed) {
                    return p;
                }
            }
        }

        return null;
    }

    private boolean hitEntitiesAlong(ServerLevel slevel, HoseSprayInteraction interaction, Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(0.3);
        boolean hit = false;
        LivingEntity who = this.attacker != null && slevel.getEntity(this.attacker) instanceof LivingEntity living ? living : null;

        for (Entity e : slevel.getEntities(this, sweep, cand -> canBeSprayed(cand) && !this.ignored.contains(cand.getUUID()))) {
            AABB box = e.getBoundingBox().inflate(0.3);
            if (box.contains(from) || box.contains(to) || box.clip(from, to).isPresent()) {
                interaction.onEntity(slevel, e, this.fluid, this.power, who);
                hit = true;
            }
        }

        return hit;
    }

    private static boolean canBeSprayed(Entity e) {
        return e.isAlive() && !e.isSpectator() && !(e instanceof HoseSprayEntity);
    }

    private static Direction stepDir(BlockPos from, BlockPos to, Vec3 v) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1 ? nearestDir(v) : Direction.fromDelta(dx, dy, dz);
    }

    private static Direction nearestDir(Vec3 v) {
        double ax = Math.abs(v.x);
        double ay = Math.abs(v.y);
        double az = Math.abs(v.z);
        if (ay >= ax && ay >= az) {
            return v.y < 0.0 ? Direction.DOWN : Direction.UP;
        } else if (ax >= az) {
            return v.x < 0.0 ? Direction.WEST : Direction.EAST;
        } else {
            return v.z < 0.0 ? Direction.NORTH : Direction.SOUTH;
        }
    }

    protected void defineSynchedData(Builder builder) {
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
