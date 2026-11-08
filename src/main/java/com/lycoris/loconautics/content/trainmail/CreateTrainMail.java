package com.lycoris.loconautics.content.trainmail;

import com.lycoris.loconautics.content.schedule.SchedulePathfinder;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainStatus.StatusMessage;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

public final class CreateTrainMail {
    private static final int BAD = 16765876;
    private static final int RECHECK_TICKS = 10;

    private CreateTrainMail() {
    }

    @Nullable
    public static DiscoveredPath deliver(ScheduleRuntime runtime, String address, String post) {
        return !hasConductor(runtime) ? null : apply(runtime, PostalPlan.deliver(new CreateTrainMail.CreateWorld(runtime), address, post));
    }

    @Nullable
    public static DiscoveredPath fetch(ScheduleRuntime runtime, String address, String post) {
        return !hasConductor(runtime) ? null : apply(runtime, PostalPlan.fetch(new CreateTrainMail.CreateWorld(runtime), address, post));
    }

    public static void recheck(ScheduleRuntime runtime, Level level) {
        PostalState state = (PostalState)runtime;
        GlobalStation target = runtime.train.navigation.destination;
        if (runtime.state == State.IN_TRANSIT && state.loconautics$repeat() && target != null && level.getGameTime() % 10L == 0L) {
            if (currentInstruction(runtime) instanceof PostalEntry entry && PostalPlan.canSkip(new CreateTrainMail.CreateWorld(runtime), entry, target)) {
                runtime.train.navigation.cancelNavigation();
            }
        }
    }

    public static void transfer(GlobalStation station, Train train) {
        if (!station.connectedPorts.isEmpty()) {
            List<IItemHandler> cargo = new ArrayList<>();

            for (Carriage carriage : train.carriages) {
                IItemHandlerModifiable items = carriage.storage.getAllItems();
                if (items != null) {
                    cargo.add(items);
                }
            }

            ScheduleRuntime runtime = train.runtime;
            PostalState state = (PostalState)runtime;
            TrainMailTransfer.Rules rules = TrainMailTransfer.Rules.of(currentInstruction(runtime), station, state.loconautics$deposit());
            TrainMailTransfer.transfer(ServerLifecycleHooks.getCurrentServer(), station, cargo, rules);
            if (rules.deposit()) {
                state.loconautics$setDeposit(false);
            }
        }
    }

    @Nullable
    private static ScheduleInstruction currentInstruction(ScheduleRuntime runtime) {
        return runtime.schedule != null && runtime.currentEntry >= 0 && runtime.currentEntry < runtime.schedule.entries.size()
            ? ((ScheduleEntry)runtime.schedule.entries.get(runtime.currentEntry)).instruction
            : null;
    }

    private static boolean hasConductor(ScheduleRuntime runtime) {
        Train train = runtime.train;
        if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
            train.status.missingConductor();
            runtime.startCooldown();
            return false;
        } else {
            return true;
        }
    }

    @Nullable
    private static DiscoveredPath apply(ScheduleRuntime runtime, PostalPlan.Step<DiscoveredPath> step) {
        PostalState state = (PostalState)runtime;
        switch (step.kind()) {
            case SKIP:
                state.loconautics$setTripped(false);
                state.loconautics$setDeposit(false);
                runtime.startCooldown();
                runtime.state = State.PRE_TRANSIT;
                runtime.currentEntry++;
                return null;
            case WAIT:
                runtime.startCooldown();
                return null;
            case FAILED:
                runtime.train.status.failedNavigation();
                runtime.startCooldown();
                return null;
            default:
                state.loconautics$setTripped(step.tripped());
                state.loconautics$setRepeat(step.repeat());
                state.loconautics$setDeposit(step.deposit());
                return step.path();
        }
    }

    private static final class CreateWorld implements PostalWorld<DiscoveredPath> {
        private final ScheduleRuntime runtime;
        private final Train train;

        private CreateWorld(ScheduleRuntime runtime) {
            this.runtime = runtime;
            this.train = runtime.train;
        }

        @Override
        public boolean tripped() {
            return ((PostalState)this.runtime).loconautics$tripped();
        }

        @Override
        public List<String> cargoAddresses(String regex) {
            List<String> addresses = new ArrayList<>();

            for (Carriage carriage : this.train.carriages) {
                IItemHandlerModifiable cargo = carriage.storage.getAllItems();
                if (cargo != null) {
                    for (int slot = 0; slot < cargo.getSlots(); slot++) {
                        ItemStack stack = cargo.getStackInSlot(slot);
                        if (PackageItem.isPackage(stack) && PackageItem.getAddress(stack).matches(regex)) {
                            addresses.add(PackageItem.getAddress(stack));
                        }
                    }
                }
            }

            return addresses;
        }

        @Override
        public List<GlobalStation> stations() {
            return this.train.graph == null ? List.of() : new ArrayList<>(this.train.graph.getPoints(EdgePointType.STATION));
        }

        @Nullable
        @Override
        public GlobalStation here() {
            return this.train.getCurrentStation();
        }

        @Override
        public MinecraftServer server() {
            return ServerLifecycleHooks.getCurrentServer();
        }

        @Nullable
        public DiscoveredPath routeTo(Collection<GlobalStation> wanted) {
            return this.train.navigation.findPathTo(new ArrayList<>(wanted), Double.MAX_VALUE);
        }

        @Override
        public void cannotDeliver(String address) {
            this.train.status.failedPackageNoTarget(address);
        }

        @Override
        public double legCost(@Nullable GlobalStation from, GlobalStation to) {
            if (from == null) {
                DiscoveredPath path = this.train.navigation.findPathTo(new ArrayList<>(List.of(to)), Double.MAX_VALUE);
                return path == null ? Double.POSITIVE_INFINITY : path.cost;
            } else {
                return this.train.graph == null ? Double.POSITIVE_INFINITY : PostalRoute.between(this.train.graph, from, to, SchedulePathfinder.Routing.NONE);
            }
        }

        @Override
        public Set<UUID> warned() {
            return ((PostalState)this.runtime).loconautics$warned();
        }

        @Override
        public void sayUnreachable(GlobalStation station) {
            MutableComponent line = Component.literal(" - ")
                .withStyle(ChatFormatting.GRAY)
                .append(
                    Component.translatable("loconautics.train.status.postal_unreachable", new Object[]{station.name})
                        .withStyle(style -> style.withColor(16765876))
                );
            this.train.status.addMessage(new StatusMessage(new Component[]{line}));
        }
    }
}
