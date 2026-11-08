package com.lycoris.loconautics.common.strand;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class StrandRemap {
    private static final List<StrandRemap.Target<?>> TARGETS = new CopyOnWriteArrayList<>();

    private StrandRemap() {
    }

    public static void register(StrandRemap.Target<?> target) {
        TARGETS.add(target);
    }

    public static void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockPos oldPos, BlockPos newPos) {
        StrandEnd from = new StrandEnd(subLevelIdAt(originLevel, oldPos), oldPos);
        StrandEnd to = new StrandEnd(subLevelIdAt(resultingLevel, newPos), newPos);
        BlockEntity oldBe = originLevel.getBlockEntity(oldPos);
        BlockEntity newBe = resultingLevel.getBlockEntity(newPos);

        for (StrandRemap.Target<?> target : TARGETS) {
            int remapped = remap(originLevel, target, from, to, oldBe, newBe);
            if (remapped > 0) {
            }
        }
    }

    private static <S> int remap(
        ServerLevel level, StrandRemap.Target<S> target, StrandEnd from, StrandEnd to, @Nullable BlockEntity oldBe, @Nullable BlockEntity newBe
    ) {
        RopeManager<?> ropes = target.ropes(level);
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        int remapped = 0;

        for (S strand : new ArrayList<>(target.strands(level))) {
            if (target.remapEnds(strand, from, to)) {
                remapped++;
                UUID id = target.idOf(strand);
                if (ropes != null && system != null) {
                    ropes.remove(system, id);
                }

                target.handOver(oldBe, newBe, id);

                for (StrandEnd end : target.endsOf(strand)) {
                    if (!end.matches(to.subLevelId(), to.local())
                        && StrandEndpoints.blockEntity(level, container, end.subLevelId(), end.local()) instanceof SmartBlockEntity far) {
                        far.sendData();
                    }
                }
            }
        }

        if (remapped > 0) {
            target.touch(level);
        }

        return remapped;
    }

    @Nullable
    private static UUID subLevelIdAt(ServerLevel level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        return sub == null ? null : sub.getUniqueId();
    }

    public interface Target<S> {
        String name();

        Collection<S> strands(ServerLevel var1);

        UUID idOf(S var1);

        boolean remapEnds(S var1, StrandEnd var2, StrandEnd var3);

        List<StrandEnd> endsOf(S var1);

        @Nullable
        RopeManager<?> ropes(ServerLevel var1);

        void touch(ServerLevel var1);

        void handOver(@Nullable BlockEntity var1, @Nullable BlockEntity var2, UUID var3);
    }
}
