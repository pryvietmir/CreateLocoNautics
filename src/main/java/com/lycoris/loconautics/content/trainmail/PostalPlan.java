package com.lycoris.loconautics.content.trainmail;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class PostalPlan {
    private static final int MAX_COVER_CANDIDATES = 12;

    private PostalPlan() {
    }

    public static <P> PostalPlan.Step<P> deliver(PostalWorld<P> world, String address, String post) {
        String postRegex = PostRows.stationRegex(post);
        List<String> cargo = world.cargoAddresses(PostRows.nameRegex(address));
        if (cargo.isEmpty()) {
            return idle(world, postRegex, false);
        }

        GlobalStation here = world.here();
        PostalRoute route = new PostalRoute(world, offices(world, postRegex));
        List<List<GlobalStation>> needs = new ArrayList<>();

        for (String packageAddress : new LinkedHashSet<>(cargo)) {
            List<GlobalStation> accepting = new ArrayList<>(accepting(world, packageAddress, here));
            accepting.removeIf(station -> !route.reachable(station));
            if (accepting.isEmpty()) {
                world.cannotDeliver(packageAddress);
            } else {
                needs.add(accepting);
            }
        }

        P path = needs.isEmpty() ? null : firstLeg(world, route, fewestStops(route, needs));
        return path != null ? new PostalPlan.Step<>(PostalPlan.Kind.GO, path, true, true) : finish(world, postRegex, false);
    }

    public static <P> PostalPlan.Step<P> fetch(PostalWorld<P> world, String address, String post) {
        String postRegex = PostRows.stationRegex(post);
        PostalRoute route = new PostalRoute(world, offices(world, postRegex));
        List<GlobalStation> stops = new ArrayList<>();

        for (GlobalStation station : world.waitingStations(PostRows.nameRegex(address))) {
            if (!PostalWorld.same(station, world.here()) && !isOffice(station, postRegex)) {
                if (route.reachable(station)) {
                    stops.add(station);
                } else {
                    world.warnUnreachable(station);
                }
            }
        }

        P path = stops.isEmpty() ? null : firstLeg(world, route, stops);
        return path != null ? new PostalPlan.Step<>(PostalPlan.Kind.GO, path, true, true) : idle(world, postRegex, true);
    }

    public static <P> boolean stillNeeded(PostalWorld<P> world, PostalEntry entry, GlobalStation target) {
        String regex = PostRows.nameRegex(entry.loconautics$address());
        if (!entry.loconautics$delivers()) {
            return MailPorts.holdsWaitingPackage(world.server(), target, regex);
        }

        for (String address : world.cargoAddresses(regex)) {
            for (GlobalPackagePort port : target.connectedPorts.values()) {
                if (PackageItem.matchAddress(address, port.address)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static <P> PostalPlan.Step<P> idle(PostalWorld<P> world, @Nullable String postRegex, boolean deposit) {
        if (world.tripped()) {
            return finish(world, postRegex, deposit);
        }

        if (postRegex == null) {
            return new PostalPlan.Step<>(PostalPlan.Kind.SKIP, null, false, false);
        }

        if (isPostOffice(world.here(), postRegex)) {
            return new PostalPlan.Step<>(PostalPlan.Kind.WAIT, null, false, false);
        }

        P path = routeToPostOffice(world, postRegex);
        return path == null ? new PostalPlan.Step<>(PostalPlan.Kind.FAILED, null, false, false) : new PostalPlan.Step<>(PostalPlan.Kind.GO, path, true, false);
    }

    private static <P> PostalPlan.Step<P> finish(PostalWorld<P> world, @Nullable String postRegex, boolean deposit) {
        if (postRegex != null && !isPostOffice(world.here(), postRegex)) {
            P path = routeToPostOffice(world, postRegex);
            return path == null
                ? new PostalPlan.Step<>(PostalPlan.Kind.FAILED, null, false, world.tripped())
                : new PostalPlan.Step<>(PostalPlan.Kind.GO, path, false, true, deposit);
        } else {
            return new PostalPlan.Step<>(PostalPlan.Kind.SKIP, null, false, false);
        }
    }

    @Nullable
    private static <P> P routeToPostOffice(PostalWorld<P> world, String postRegex) {
        List<GlobalStation> offices = new ArrayList<>();

        for (GlobalStation station : offices(world, postRegex)) {
            if (!PostalWorld.same(station, world.here())) {
                offices.add(station);
            }
        }

        return offices.isEmpty() ? null : world.routeTo(offices);
    }

    private static <P> List<GlobalStation> offices(PostalWorld<P> world, @Nullable String postRegex) {
        List<GlobalStation> offices = new ArrayList<>();
        if (postRegex == null) {
            return offices;
        }

        for (GlobalStation station : world.stations()) {
            if (station.name.matches(postRegex)) {
                offices.add(station);
            }
        }

        return offices;
    }

    @Nullable
    private static <P> P firstLeg(PostalWorld<P> world, PostalRoute route, List<GlobalStation> stops) {
        GlobalStation first = route.cheapest(stops).stops().get(0);
        return world.routeTo(List.of(first));
    }

    private static List<GlobalStation> fewestStops(PostalRoute route, List<List<GlobalStation>> needs) {
        List<GlobalStation> candidates = new ArrayList<>();

        for (List<GlobalStation> accepting : needs) {
            for (GlobalStation station : accepting) {
                if (!contains(candidates, station)) {
                    candidates.add(station);
                }
            }
        }

        if (candidates.size() > 12) {
            return greedyCover(candidates, needs);
        }

        List<GlobalStation> best = null;
        double bestCost = Double.POSITIVE_INFINITY;

        for (int size = 1; size <= candidates.size() && best == null; size++) {
            for (List<GlobalStation> subset : subsets(candidates, size)) {
                if (covers(subset, needs)) {
                    double cost = route.cheapest(subset).cost();
                    if (best == null || cost < bestCost) {
                        best = subset;
                        bestCost = cost;
                    }
                }
            }
        }

        return best == null ? candidates : best;
    }

    private static List<GlobalStation> greedyCover(List<GlobalStation> candidates, List<List<GlobalStation>> needs) {
        List<List<GlobalStation>> left = new ArrayList<>(needs);
        List<GlobalStation> chosen = new ArrayList<>();

        while (!left.isEmpty()) {
            GlobalStation top = candidates.get(0);
            long topCount = -1L;

            for (GlobalStation station : candidates) {
                long count = left.stream().filter(accepting -> contains((List<GlobalStation>)accepting, station)).count();
                if (count > topCount) {
                    top = station;
                    topCount = count;
                }
            }

            GlobalStation pick = top;
            chosen.add(pick);
            left.removeIf(accepting -> contains((List<GlobalStation>)accepting, pick));
        }

        return chosen;
    }

    private static boolean covers(List<GlobalStation> subset, List<List<GlobalStation>> needs) {
        for (List<GlobalStation> accepting : needs) {
            if (subset.stream().noneMatch(station -> contains(accepting, station))) {
                return false;
            }
        }

        return true;
    }

    private static boolean contains(List<GlobalStation> stations, GlobalStation station) {
        return stations.stream().anyMatch(other -> PostalWorld.same(other, station));
    }

    private static List<List<GlobalStation>> subsets(List<GlobalStation> all, int size) {
        List<List<GlobalStation>> out = new ArrayList<>();
        collect(all, size, 0, new ArrayList<>(), out);
        return out;
    }

    private static void collect(List<GlobalStation> all, int size, int from, List<GlobalStation> picked, List<List<GlobalStation>> out) {
        if (picked.size() == size) {
            out.add(new ArrayList<>(picked));
        } else {
            for (int i = from; i < all.size(); i++) {
                picked.add(all.get(i));
                collect(all, size, i + 1, picked, out);
                picked.remove(picked.size() - 1);
            }
        }
    }

    private static boolean isOffice(GlobalStation station, @Nullable String postRegex) {
        return postRegex != null && station.name.matches(postRegex);
    }

    public static <P> PostalPlan.Step<P> plan(PostalWorld<P> world, PostalEntry entry) {
        return entry.loconautics$delivers()
            ? deliver(world, entry.loconautics$address(), entry.loconautics$post())
            : fetch(world, entry.loconautics$address(), entry.loconautics$post());
    }

    public static <P> boolean canSkip(PostalWorld<P> world, PostalEntry entry, GlobalStation target) {
        return !stillNeeded(world, entry, target) && plan(world, entry).kind() == PostalPlan.Kind.GO;
    }

    private static boolean isPostOffice(@Nullable GlobalStation station, String postRegex) {
        return station != null && station.name.matches(postRegex);
    }

    private static <P> List<GlobalStation> accepting(PostalWorld<P> world, String address, @Nullable GlobalStation here) {
        List<GlobalStation> out = new ArrayList<>();

        for (GlobalStation station : world.stations()) {
            if (!PostalWorld.same(station, here)) {
                for (GlobalPackagePort port : station.connectedPorts.values()) {
                    if (PackageItem.matchAddress(address, port.address)) {
                        out.add(station);
                        break;
                    }
                }
            }
        }

        return out;
    }

    public enum Kind {
        SKIP,
        WAIT,
        GO,
        FAILED;
    }

    public record Step<P>(PostalPlan.Kind kind, @Nullable P path, boolean repeat, boolean tripped, boolean deposit) {
        public Step(PostalPlan.Kind kind, @Nullable P path, boolean repeat, boolean tripped) {
            this(kind, path, repeat, tripped, false);
        }
    }
}
