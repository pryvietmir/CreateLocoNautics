package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainLength;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;

public final class TrainSyncCache {
    static final Map<UUID, Double> SYNCED_SPEED = new HashMap<>();
    static final Map<UUID, Double> SYNCED_WHEELDRIVE = new HashMap<>();
    static final Set<UUID> SYNCED_DERAILED = new HashSet<>();
    static final Set<UUID> SYNCED_BRAKED = new HashSet<>();
    static final Map<UUID, Integer> SYNCED_OFFTRACK = new HashMap<>();
    static final Map<UUID, Double> SYNCED_CONSIST = new HashMap<>();
    static final Map<UUID, Map<BlockPos, Double>> SYNCED_DRIVEN = new HashMap<>();
    static final Map<UUID, Set<BlockPos>> SYNCED_SLIPPING = new HashMap<>();
    static final Map<UUID, Double> SYNCED_SLIP = new HashMap<>();
    static final Map<UUID, Double> SYNCED_TOP = new HashMap<>();

    private TrainSyncCache() {
    }

    static void retain(Set<UUID> live) {
        SYNCED_SPEED.keySet().retainAll(live);
        SYNCED_WHEELDRIVE.keySet().retainAll(live);
        SYNCED_DERAILED.retainAll(live);
        SYNCED_BRAKED.retainAll(live);
        SYNCED_OFFTRACK.keySet().retainAll(live);
        SYNCED_CONSIST.keySet().retainAll(live);
        SYNCED_DRIVEN.keySet().retainAll(live);
        SYNCED_SLIPPING.keySet().retainAll(live);
        SYNCED_SLIP.keySet().retainAll(live);
        SYNCED_TOP.keySet().retainAll(live);
    }

    public static double syncedConsistLength(SableTrain train) {
        Double known = SYNCED_CONSIST.get(train.id());
        return known != null ? known : TrainLength.consistLength(train.level(), train.car().subLevelId());
    }

    static void sync(SableTrain train) {
        double speed = train.speed();
        double wheelDrive = train.wheelDrive();
        boolean derailed = train.isDerailed();
        boolean braked = train.brakeHard();
        int offTrack = train.offTrackBogeyCount();
        Double last = SYNCED_SPEED.get(train.id());
        Double lastWheel = SYNCED_WHEELDRIVE.get(train.id());
        boolean lastDerailed = SYNCED_DERAILED.contains(train.id());
        boolean lastBraked = SYNCED_BRAKED.contains(train.id());
        Integer lastOffTrack = SYNCED_OFFTRACK.get(train.id());
        Map<BlockPos, Double> driven = train.drivenBogeys();
        Set<BlockPos> slipping = train.slippingBogeys();
        double slip = train.slipFactor();
        Double lastSlip = SYNCED_SLIP.get(train.id());
        double top = TrainPropulsion.gearedTopBpt(train);
        Double lastTop = SYNCED_TOP.get(train.id());
        Double lastConsist = SYNCED_CONSIST.get(train.id());
        double consist = lastConsist != null && train.level().getGameTime() % 20L != 0L
            ? lastConsist
            : TrainLength.consistLength(train.level(), train.car().subLevelId());
        if (last == null
            || !(Math.abs(last - speed) < 0.004)
            || settledToZero(last, speed)
            || lastWheel == null
            || !(Math.abs(lastWheel - wheelDrive) < 0.004)
            || settledToZero(lastWheel, wheelDrive)
            || derailed != lastDerailed
            || braked != lastBraked
            || lastOffTrack == null
            || lastOffTrack != offTrack
            || lastSlip == null
            || !(Math.abs(lastSlip - slip) < 0.01)
            || lastTop == null
            || !(Math.abs(lastTop - top) <= lastTop * 0.01)
            || lastConsist == null
            || !(Math.abs(lastConsist - consist) < 0.01)
            || !slipping.equals(SYNCED_SLIPPING.getOrDefault(train.id(), Set.of()))
            || !sameDriven(SYNCED_DRIVEN.get(train.id()), driven)) {
            SYNCED_DRIVEN.put(train.id(), new LinkedHashMap<>(driven));
            SYNCED_SLIPPING.put(train.id(), Set.copyOf(slipping));
            SYNCED_SLIP.put(train.id(), slip);
            SYNCED_TOP.put(train.id(), top);
            SYNCED_SPEED.put(train.id(), speed);
            SYNCED_WHEELDRIVE.put(train.id(), wheelDrive);
            SYNCED_OFFTRACK.put(train.id(), offTrack);
            SYNCED_CONSIST.put(train.id(), consist);
            if (derailed) {
                SYNCED_DERAILED.add(train.id());
            } else {
                SYNCED_DERAILED.remove(train.id());
            }

            if (braked) {
                SYNCED_BRAKED.add(train.id());
            } else {
                SYNCED_BRAKED.remove(train.id());
            }

            SableTrainSyncPacket.broadcast(train);
        }
    }

    private static boolean settledToZero(double last, double now) {
        return Math.abs(now) < 0.001 && last != now;
    }

    private static boolean sameDriven(Map<BlockPos, Double> last, Map<BlockPos, Double> now) {
        if (last != null && last.size() == now.size()) {
            for (Entry<BlockPos, Double> entry : now.entrySet()) {
                Double previous = last.get(entry.getKey());
                if (previous == null || Math.abs(previous - entry.getValue()) >= 0.004 || settledToZero(previous, entry.getValue())) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }
}
