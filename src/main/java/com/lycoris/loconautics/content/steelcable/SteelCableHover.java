package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.ClientStrands;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.common.strand.StrandRenderer;
import com.lycoris.loconautics.network.packets.attachment.SteelCableBreakPacket;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SteelCableHover {
    private static final double HALF_THICKNESS = 0.25;
    @Nullable
    public static UUID hovered;
    private static final Vector3d UP = new Vector3d(0.0, 1.0, 0.0);

    private SteelCableHover() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null && isWrench(player.getMainHandItem())) {
            double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0;
            Vec3 eye = player.getEyePosition();
            Vec3 target = RaycastHelper.getTraceTarget(player, reach, eye);
            HitResult hit = mc.hitResult;
            double bestSqr = hit == null ? Double.MAX_VALUE : Sable.HELPER.projectOutOfSubLevel(mc.level, hit.getLocation()).distanceToSqr(eye);
            hovered = raycast(new Vector3d(eye.x, eye.y, eye.z), new Vector3d(target.x, target.y, target.z), bestSqr);
        } else {
            hovered = null;
        }
    }

    @Nullable
    private static UUID raycast(Vector3dc from, Vector3dc to, double bestSqr) {
        Vector3d localFrom = new Vector3d();
        Vector3d localTo = new Vector3d();
        Vector3d normal = new Vector3d();
        UUID best = null;

        for (Entry<UUID, ClientStrands.Strand> entry : entries()) {
            List<ClientStrands.Point> points = entry.getValue().points();

            for (int i = 0; i < points.size() - 1; i++) {
                Vector3d a = points.get(i).position;
                Vector3d b = points.get(i + 1).position;
                double length = a.distance(b);
                if (!(length < 1.0E-6)) {
                    b.sub(a, normal).normalize();
                    AABB bounds = new AABB(-0.25, 0.0, -0.25, 0.25, length, 0.25);
                    Quaternionf rot = StrandRenderer.rotationTo(UP, normal);
                    rot.transformInverse(localFrom.set(from).sub(a));
                    rot.transformInverse(localTo.set(to).sub(a));
                    Optional<Vec3> clip = bounds.clip(new Vec3(localFrom.x, localFrom.y, localFrom.z), new Vec3(localTo.x, localTo.y, localTo.z));
                    if (!clip.isEmpty()) {
                        double distSqr = clip.get().distanceToSqr(localFrom.x, localFrom.y, localFrom.z);
                        if (!(distSqr > bestSqr)) {
                            bestSqr = distSqr;
                            best = entry.getKey();
                        }
                    }
                }
            }
        }

        return best;
    }

    public static boolean isWrench(ItemStack stack) {
        return AllItems.WRENCH.isIn(stack);
    }

    public static boolean onUse() {
        Minecraft mc = Minecraft.getInstance();
        if (hovered != null && mc.player != null && isWrench(mc.player.getMainHandItem())) {
            CatnipServices.NETWORK.sendToServer(new SteelCableBreakPacket(hovered));
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        } else {
            return false;
        }
    }

    private static Iterable<Entry<UUID, ClientStrands.Strand>> entries() {
        List<Entry<UUID, ClientStrands.Strand>> all = new ArrayList<>(StrandKind.STEEL_CABLE.clientStrands().entries());
        all.addAll(StrandKind.HOSE_LINK.clientStrands().entries());
        return all;
    }
}
