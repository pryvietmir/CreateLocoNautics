package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.content.hose.HoseStrand;
import com.lycoris.loconautics.content.hose.HoseStrandManager;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandDriver;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.lycoris.loconautics.network.PacketTargets;
import dev.ryanhcode.sable.Sable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import org.joml.Quaterniond;
import org.joml.Vector3d;

@EventBusSubscriber(modid = "loconautics")
public final class StrandShearing {
    private static final double RADIUS = 0.25;

    private StrandShearing() {
    }

    @SubscribeEvent
    public static void onUseItem(RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && cut(player, event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && cut(player, event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean cut(ServerPlayer player, InteractionHand hand) {
        if (!player.isSpectator()
            && player.mayBuild()
            && !player.getCooldowns().isOnCooldown(player.getItemInHand(hand).getItem())
            && player.getItemInHand(hand).canPerformAction(ItemAbilities.SHEARS_CARVE)) {
            ServerLevel level = player.serverLevel();
            Vec3 eye = player.getEyePosition();
            double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
            Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
            double limit = reach * reach;
            HitResult obstruction = player.pick(reach, 1.0F, false);
            if (obstruction.getType() != Type.MISS) {
                limit = Math.min(limit, Sable.HELPER.projectOutOfSubLevel(level, obstruction.getLocation()).distanceToSqr(eye));
            }

            StrandShearing.Hit nearest = null;
            RopeManager<?>[] managers = new RopeManager[]{SteelCableRopeManager.get(level), HoseRopeManager.get(level)};

            for (int kind = 0; kind < managers.length; kind++) {
                RopeManager<?> manager = managers[kind];
                if (manager != null) {
                    for (AttachedRopePhysicsObject rope : manager.all()) {
                        if (rope.isActive() && rope.areAttachmentsLoaded(level)) {
                            double distance = hitDistanceSquared(eye, end, rope.getPoints());
                            if (distance < limit) {
                                limit = distance;
                                nearest = new StrandShearing.Hit(kind, rope.getUUID(), distance);
                            }
                        }
                    }
                }
            }

            if (nearest != null && remove(level, nearest)) {
                player.getCooldowns().addCooldown(player.getItemInHand(hand).getItem(), 4);
                if (!player.isCreative()) {
                    player.getItemInHand(hand).hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                }

                player.swing(hand, true);
                Vec3 at = eye.add(player.getViewVector(1.0F).scale(Math.sqrt(nearest.distanceSquared())));
                level.playSound(null, at.x, at.y, at.z, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static double hitDistanceSquared(Vec3 from, Vec3 to, List<Vector3d> points) {
        double best = Double.POSITIVE_INFINITY;

        for (int i = 1; i < points.size(); i++) {
            Vector3d a = points.get(i - 1);
            Vector3d b = points.get(i);
            double length = a.distance(b);
            if (Double.isFinite(length) && !(length < 1.0E-6)) {
                Quaterniond rotation = new Quaterniond().rotationTo(new Vector3d(0.0, 1.0, 0.0), b.sub(a, new Vector3d()).normalize());
                Vector3d localFrom = rotation.transformInverse(new Vector3d(from.x, from.y, from.z).sub(a));
                Vector3d localTo = rotation.transformInverse(new Vector3d(to.x, to.y, to.z).sub(a));
                Vec3 origin = new Vec3(localFrom.x, localFrom.y, localFrom.z);
                AABB bounds = new AABB(-0.25, 0.0, -0.25, 0.25, length, 0.25);
                if (bounds.contains(origin)) {
                    return 0.0;
                }

                Optional<Vec3> hit = bounds.clip(origin, new Vec3(localTo.x, localTo.y, localTo.z));
                if (hit.isPresent()) {
                    best = Math.min(best, origin.distanceToSqr(hit.get()));
                }
            }
        }

        return best;
    }

    private static boolean remove(ServerLevel level, StrandShearing.Hit hit) {
        if (hit.kind() == 0) {
            SteelCableStrandManager strands = SteelCableStrandManager.get(level);
            SteelCableRopeManager ropes = SteelCableRopeManager.get(level);
            SteelCableStrand strand = strands == null ? null : strands.get(hit.id());
            if (strand != null && ropes != null) {
                SteelCableStrandDriver.breakStrand(level, strands, ropes, strand, null);
                return true;
            } else {
                return false;
            }
        } else if (hit.kind() == 1) {
            HoseStrandManager strands = HoseStrandManager.get(level);
            HoseStrand strand = strands == null ? null : strands.get(hit.id());
            if (strand == null) {
                return false;
            } else {
                HoseWinchBlockEntity winch = PacketTargets.blockEntity(level, strand.winchPos, HoseWinchBlockEntity.class);
                if (winch != null && winch.getAttachedStrand() == strand) {
                    winch.onWinchBroken();
                    return strands.get(hit.id()) == null;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    private record Hit(int kind, UUID id, double distanceSquared) {
    }
}
