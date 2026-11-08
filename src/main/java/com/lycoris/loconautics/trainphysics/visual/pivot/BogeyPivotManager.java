package com.lycoris.loconautics.trainphysics.visual.pivot;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import org.joml.Quaterniond;

@EventBusSubscriber(modid = "loconautics")
public final class BogeyPivotManager {
    private static final Map<BogeyPivotManager.PivotKey, BogeyPivotBody> BODIES = new HashMap<>();

    private BogeyPivotManager() {
    }

    public static void clear() {
        BODIES.clear();
    }

    public static void register() {
        SableEventPlatform.INSTANCE.onPhysicsTick(BogeyPivotManager::onPrePhysicsTick);
    }

    static void onPrePhysicsTick(SubLevelPhysicsSystem system, double dt) {
        if ((Boolean)LoconauticsConfig.BOGEY_PHYSICS_PIVOT.get()) {
            ServerLevel level = system.getLevel();

            for (SableTrain train : SableTrainRegistry.all()) {
                if (train.level() == level && !train.isDerailed() && !train.isPhysics() && train.car() != null) {
                    UUID subId = train.car().subLevelId();
                    if (subId != null) {
                        for (SableTrain.Bogey bogey : train.car().bogeys()) {
                            BogeyPivotManager.PivotKey key = new BogeyPivotManager.PivotKey(subId, bogey.localPos());
                            BogeyPivotBody body = BODIES.computeIfAbsent(key, k -> new BogeyPivotBody());
                            Quaterniond neutral = bogey.rail().leadingTangentQuaternion();
                            Quaterniond target = new Quaterniond(neutral);
                            body.drive(dt, target);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(Post event) {
        if (!BODIES.isEmpty()) {
            boolean enabled = (Boolean)LoconauticsConfig.BOGEY_PHYSICS_PIVOT.get();
            Set<BogeyPivotManager.PivotKey> live = new HashSet<>();
            if (enabled) {
                for (SableTrain train : SableTrainRegistry.all()) {
                    if (!train.isDerailed() && !train.isPhysics()) {
                        ServerSubLevel sub = resolveSub(train);
                        if (sub != null) {
                            UUID subId = train.car().subLevelId();
                            ServerLevel level = sub.getLevel();

                            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                                BogeyPivotManager.PivotKey key = new BogeyPivotManager.PivotKey(subId, bogey.localPos());
                                live.add(key);
                                BogeyPivotBody body = BODIES.get(key);
                                if (body == null) {
                                }
                            }
                        }
                    }
                }
            }

            Iterator<Entry<BogeyPivotManager.PivotKey, BogeyPivotBody>> it = BODIES.entrySet().iterator();

            while (it.hasNext()) {
                BogeyPivotManager.PivotKey key = it.next().getKey();
                if (!enabled || !live.contains(key)) {
                    it.remove();
                }
            }
        }
    }

    private static ServerSubLevel resolveSub(SableTrain train) {
        UUID subId = train.car() == null ? null : train.car().subLevelId();
        if (subId == null) {
            return null;
        } else {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            if (container == null) {
                return null;
            } else {
                return container.getSubLevel(subId) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
            }
        }
    }

    private record PivotKey(UUID subLevel, BlockPos local) {
    }
}
