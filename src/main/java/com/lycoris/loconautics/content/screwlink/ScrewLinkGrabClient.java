package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.ClientStrands;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class ScrewLinkGrabClient {
    private static final double HOOK_HALF_EXTENT = 0.25;

    private ScrewLinkGrabClient() {
    }

    @Nullable
    public static UUID pickHook() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
            Vec3 eye = player.getEyePosition();
            Vec3 target = RaycastHelper.getTraceTarget(player, reach, eye);
            HitResult hit = mc.hitResult;
            double bestSqr = hit == null ? reach * reach : Sable.HELPER.projectOutOfSubLevel(mc.level, hit.getLocation()).distanceToSqr(eye);
            UUID best = null;

            for (Entry<UUID, ClientStrands.Strand> entry : StrandKind.SCREW_LINK.clientStrands().entries()) {
                List<ClientStrands.Point> points = entry.getValue().points();
                if (points.size() >= 2) {
                    Vector3d end = points.get(points.size() - 1).position;
                    AABB box = new AABB(end.x - 0.25, end.y - 0.25, end.z - 0.25, end.x + 0.25, end.y + 0.25, end.z + 0.25);
                    Optional<Vec3> clip = box.clip(eye, target);
                    if (!clip.isEmpty()) {
                        double distSqr = clip.get().distanceToSqr(eye);
                        if (!(distSqr > bestSqr)) {
                            bestSqr = distSqr;
                            best = entry.getKey();
                        }
                    }
                }
            }

            return best;
        } else {
            return null;
        }
    }

    @Nullable
    public static Vec3 hookPosition(UUID strandId, float partialTick) {
        ClientStrands.Strand strand = StrandKind.SCREW_LINK.clientStrands().get(strandId);
        if (strand != null && strand.size() != 0) {
            Vector3d end = strand.points().get(strand.size() - 1).renderPos(partialTick, new Vector3d());
            return new Vec3(end.x, end.y, end.z);
        } else {
            return null;
        }
    }

    public static boolean stillLoaded(UUID strandId) {
        return StrandKind.SCREW_LINK.clientStrands().get(strandId) != null;
    }
}
