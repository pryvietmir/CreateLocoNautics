package com.lycoris.loconautics.content.trainmail;

import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.content.schedule.RouteTraffic;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.schedule.SchedulePathfinder;
import com.lycoris.loconautics.content.schedule.StationRouting;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class SablePostalWorld implements PostalWorld<SchedulePath> {
    private final SableScheduleRuntime runtime;
    private final SableTrain train;
    @Nullable
    private SchedulePathfinder.Routing routing;

    public SablePostalWorld(SableScheduleRuntime runtime) {
        this.runtime = runtime;
        this.train = runtime.train();
    }

    @Override
    public boolean tripped() {
        return this.runtime.loconautics$tripped();
    }

    @Override
    public List<String> cargoAddresses(String regex) {
        List<String> addresses = new ArrayList<>();

        for (IItemHandler items : ConsistAccess.itemsIn(this.train.level(), this.train.car().subLevelId(), -1)) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (PackageItem.isPackage(stack) && PackageItem.getAddress(stack).matches(regex)) {
                    addresses.add(PackageItem.getAddress(stack));
                }
            }
        }

        return addresses;
    }

    @Override
    public List<GlobalStation> stations() {
        RailCarriage rail = TrainStations.noseRail(this.train);
        TrackGraph graph = rail == null ? null : rail.graph();
        return graph == null ? List.of() : new ArrayList<>(graph.getPoints(EdgePointType.STATION));
    }

    @Nullable
    @Override
    public GlobalStation here() {
        return this.train.currentStation() != null ? this.train.currentStation() : this.train.atStation();
    }

    @Override
    public MinecraftServer server() {
        return this.train.level().getServer();
    }

    @Nullable
    public SchedulePath routeTo(Collection<GlobalStation> wanted) {
        StationRouting.Plan plan = StationRouting.plan(this.runtime, station -> wanted.stream().anyMatch(other -> PostalWorld.same(other, station)), false);
        return plan == null ? null : plan.best;
    }

    @Override
    public void cannotDeliver(String address) {
        TrainStatusChat.failedPackageNoTarget(this.train, address);
    }

    @Override
    public double legCost(@Nullable GlobalStation from, GlobalStation to) {
        if (from != null) {
            RailCarriage rail = TrainStations.noseRail(this.train);
            TrackGraph graph = rail == null ? null : rail.graph();
            if (graph == null) {
                return Double.POSITIVE_INFINITY;
            }

            if (this.routing == null) {
                this.routing = SchedulePathfinder.Routing.through(TrainLength.consistLength(this.train.level(), this.train.car().subLevelId()))
                    .around(RouteTraffic.around(this.train, graph));
            }

            return PostalRoute.between(graph, from, to, this.routing);
        } else {
            StationRouting.Plan plan = StationRouting.plan(this.runtime, station -> PostalWorld.same(station, to), false);
            return plan != null && plan.best != null ? plan.bestCost : Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public Set<UUID> warned() {
        return this.runtime.loconautics$warned();
    }

    @Override
    public void sayUnreachable(GlobalStation station) {
        TrainStatusChat.postalUnreachable(this.train, station.name);
    }
}
