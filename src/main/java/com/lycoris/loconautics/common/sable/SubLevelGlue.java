package com.lycoris.loconautics.common.sable;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SubLevelGlue {
    private SubLevelGlue() {
    }

    public static List<SubLevelGlue.Strip> take(ServerSubLevel sub) {
        ServerLevel level = sub.getLevel();
        Vec3 shift = corner(sub).scale(-1.0);
        AABB plot = plotArea(sub);
        List<SubLevelGlue.Strip> out = new ArrayList<>();

        for (SuperGlueEntity g : level.getEntitiesOfClass(SuperGlueEntity.class, plot)) {
            out.add(new SubLevelGlue.Strip(g.getBoundingBox().move(shift), false));
            g.remove(RemovalReason.KILLED);
        }

        try {
            takeHoney(level, plot, shift, out);
        } catch (NoClassDefFoundError var7) {
        }

        return out;
    }

    public static void put(ServerSubLevel sub, List<SubLevelGlue.Strip> strips) {
        if (!strips.isEmpty()) {
            ServerLevel level = sub.getLevel();
            Vec3 shift = corner(sub);

            for (SubLevelGlue.Strip strip : strips) {
                AABB box = strip.local().move(shift);
                if (!strip.honey()) {
                    level.addFreshEntity(new SuperGlueEntity(level, box));
                } else {
                    try {
                        putHoney(level, box);
                    } catch (NoClassDefFoundError var8) {
                    }
                }
            }
        }
    }

    private static void takeHoney(ServerLevel level, AABB plot, Vec3 shift, List<SubLevelGlue.Strip> out) {
        for (HoneyGlueEntity g : level.getEntitiesOfClass(HoneyGlueEntity.class, plot)) {
            out.add(new SubLevelGlue.Strip(g.getBoundingBox().move(shift), true));
            g.remove(RemovalReason.KILLED);
        }
    }

    private static void putHoney(ServerLevel level, AABB box) {
        HoneyGlueEntity glue = new HoneyGlueEntity(level, box);
        level.addFreshEntity(glue);
        glue.setBoundsAndSync(box);
    }

    private static Vec3 corner(ServerSubLevel sub) {
        BlockPos center = sub.getPlot().getCenterBlock();
        return new Vec3(center.getX(), center.getY(), center.getZ());
    }

    private static AABB plotArea(ServerSubLevel sub) {
        BoundingBox3ic box = sub.getPlot().getBoundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0);
    }

    public record Strip(AABB local, boolean honey) {
    }
}
