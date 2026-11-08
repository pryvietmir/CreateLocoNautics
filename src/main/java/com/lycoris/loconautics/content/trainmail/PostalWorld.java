package com.lycoris.loconautics.content.trainmail;

import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public interface PostalWorld<P> {
    boolean tripped();

    List<String> cargoAddresses(String var1);

    List<GlobalStation> stations();

    @Nullable
    GlobalStation here();

    MinecraftServer server();

    @Nullable
    P routeTo(Collection<GlobalStation> var1);

    void cannotDeliver(String var1);

    double legCost(@Nullable GlobalStation var1, GlobalStation var2);

    Set<UUID> warned();

    void sayUnreachable(GlobalStation var1);

    default void warnUnreachable(GlobalStation station) {
        if (this.warned().add(station.id)) {
            this.sayUnreachable(station);
        }
    }

    static boolean same(@Nullable GlobalStation a, @Nullable GlobalStation b) {
        return a == b || a != null && b != null && a.id != null && a.id.equals(b.id);
    }

    default List<GlobalStation> waitingStations(String regex) {
        List<GlobalStation> out = new ArrayList<>();
        GlobalStation here = this.here();

        for (GlobalStation station : this.stations()) {
            if (!same(station, here) && MailPorts.holdsWaitingPackage(this.server(), station, regex)) {
                out.add(station);
            }
        }

        return out;
    }
}
