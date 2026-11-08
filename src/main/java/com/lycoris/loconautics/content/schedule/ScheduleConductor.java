package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.condition.ScheduledDelay;
import com.lycoris.loconautics.content.schedule.destination.DestinationInstruction;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.network.packets.schedule.BlazeConductorHatPacket;
import com.lycoris.loconautics.network.packets.schedule.ConductorHatPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "loconautics")
public final class ScheduleConductor {
    public static final String TRAIN_HAT = "TrainHat";
    private static final String STATION_SCHEDULE = "StationSchedule";
    private static final String ADV_TRAIN = "AdvTrain";

    private ScheduleConductor() {
    }

    @SubscribeEvent
    public static void onInteractWithSeatedMob(EntityInteractSpecific event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        if (player != null && target != null && !player.isSpectator()) {
            if (target instanceof LivingEntity mob) {
                if (mob.getVehicle() != null) {
                    if (!player.getCooldowns().isOnCooldown((Item)AllItems.SCHEDULE.get())) {
                        if (subLevelOf(mob.getVehicle()) != null) {
                            ItemStack held = event.getItemStack();
                            boolean onServer = !event.getLevel().isClientSide;
                            boolean isConductor = mob.getPersistentData().contains("TrainHat");
                            if (AllItems.SCHEDULE.isIn(held)) {
                                if (onServer) {
                                    handOverSchedule(player, mob, held, isConductor);
                                }

                                consume(event, player);
                            } else {
                                if (held.isEmpty() && isConductor) {
                                    if (event.getHand() == InteractionHand.OFF_HAND) {
                                        return;
                                    }

                                    if (onServer) {
                                        if (resumePaused(mob, player)) {
                                            consume(event, player);
                                            return;
                                        }

                                        CompoundTag carried = takeFrom(mob);
                                        PacketDistributor.sendToPlayersTrackingEntity(
                                            mob, new ConductorHatPacket(mob.getId(), false), new CustomPacketPayload[0]
                                        );
                                        AllSoundEvents.playItemPickup(player);
                                        player.displayClientMessage(
                                            Component.translatable("loconautics.schedule.removed_conductor", new Object[]{mob.getDisplayName()}), true
                                        );
                                        if (carried != null) {
                                            ItemStack returned = AllItems.SCHEDULE.asStack();
                                            returned.set(AllDataComponents.TRAIN_SCHEDULE, carried);
                                            player.getInventory().placeItemBackInInventory(returned);
                                        }
                                    }

                                    consume(event, player);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onInteractWithBlazeDriver(RightClickBlock event) {
        Player player = event.getEntity();
        if (player != null && !player.isSpectator() && event.getHand() == InteractionHand.MAIN_HAND) {
            Level level = event.getLevel();
            BlockPos pos = event.getPos();
            if (level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner) {
                if (burner.getHeatLevelFromBlock() != HeatLevel.NONE) {
                    if (!player.getCooldowns().isOnCooldown((Item)AllItems.SCHEDULE.get())) {
                        if (subLevelAt(level, pos) != null) {
                            ItemStack held = event.getItemStack();
                            if (AllItems.SCHEDULE.isIn(held)) {
                                if (!level.isClientSide) {
                                    handOverToBlaze(player, level, pos, held, burner);
                                }

                                player.getCooldowns().addCooldown((Item)AllItems.SCHEDULE.get(), 5);
                                event.setCancellationResult(InteractionResult.SUCCESS);
                                event.setCanceled(true);
                            } else if (held.isEmpty() && !level.isClientSide) {
                                if (takeBackFromBlaze(player, level, pos, burner)) {
                                    player.getCooldowns().addCooldown((Item)AllItems.SCHEDULE.get(), 5);
                                    event.setCancellationResult(InteractionResult.SUCCESS);
                                    event.setCanceled(true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void handOverToBlaze(Player player, Level level, BlockPos pos, ItemStack held, BlazeBurnerBlockEntity burner) {
        ServerSubLevel sub = subLevelAt(level, pos);
        SableTrain train = trainOf(sub);
        if (train == null) {
            deny(player, level, pos, "no_train");
        } else {
            AnalogControllerBlockEntity controller = ScheduleReadiness.controllerAt(sub, pos);
            if (controller == null) {
                deny(player, level, pos, "burner_not_facing");
            } else if (SableScheduleRuntime.consistKey(train) != null) {
                joinAsBlaze(player, level, pos, sub, controller, burner);
            } else {
                Schedule schedule = validateSchedule(player, level, pos, sub, controller, held);
                if (schedule != null) {
                    ScheduleProgressStore.get(level.getServer()).putSchedule(train.id(), schedule.write(player.registryAccess()));
                    SableScheduleRuntime.assign(train.id(), schedule);
                    setBlazeHat(sub, burner, pos, true);
                    AllSoundEvents.CONFIRM.playOnServer(level, pos, 1.0F, 1.0F);
                    TrainAdvancements.conductor(player);
                    player.displayClientMessage(Component.translatable("loconautics.schedule.blaze_now_driver"), true);
                    held.shrink(1);
                }
            }
        }
    }

    private static void joinAsBlaze(
        Player player, Level level, BlockPos pos, ServerSubLevel sub, AnalogControllerBlockEntity controller, BlazeBurnerBlockEntity burner
    ) {
        if (burner.hat) {
            deny(player, level, pos, "already_assigned");
        } else if (hardwareOk(player, level, pos, sub, controller)) {
            setBlazeHat(sub, burner, pos, true);
            AllSoundEvents.CONFIRM.playOnServer(level, pos, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("loconautics.schedule.blaze_co_driver"), true);
        }
    }

    public static boolean drives(LivingEntity mob, UUID trainId) {
        CompoundTag data = mob.getPersistentData();
        return data.contains("TrainHat") && data.hasUUID("AdvTrain") && data.getUUID("AdvTrain").equals(trainId);
    }

    private static boolean resumePaused(LivingEntity mob, Player player) {
        CompoundTag data = mob.getPersistentData();
        return data.hasUUID("AdvTrain") && announceResume(data.getUUID("AdvTrain"), player);
    }

    private static boolean announceResume(UUID trainId, Player player) {
        if (!SableScheduleRuntime.resume(trainId)) {
            return false;
        }

        AllSoundEvents.CONFIRM.playOnServer(player.level(), player.blockPosition(), 1.0F, 1.0F);
        player.displayClientMessage(CreateLang.translateDirect("schedule.continued", new Object[0]), true);
        return true;
    }

    private static boolean takeBackFromBlaze(Player player, Level level, BlockPos pos, BlazeBurnerBlockEntity burner) {
        SableTrain train = trainOf(subLevelAt(level, pos));
        if (train == null) {
            return false;
        }

        if (announceResume(train.id(), player)) {
            return true;
        }

        if (!burner.hat) {
            return false;
        }

        CompoundTag tag = takeConsistSchedule(level, train.id());
        setBlazeHat(subLevelAt(level, pos), burner, pos, false);
        AllSoundEvents.playItemPickup(player);
        player.displayClientMessage(Component.translatable("loconautics.schedule.blaze_relieved"), true);
        if (tag != null) {
            ItemStack returned = AllItems.SCHEDULE.asStack();
            returned.set(AllDataComponents.TRAIN_SCHEDULE, tag);
            player.getInventory().placeItemBackInInventory(returned);
        }

        return true;
    }

    public static void returnScheduleOnDisassembly(ServerLevel level, UUID trainId, ServerSubLevel sub, Vec3 dropAt, @Nullable ServerPlayer sender) {
        BlockPos at = BlockPos.containing(dropAt.x, dropAt.y, dropAt.z);
        CompoundTag legacy = null;

        for (Entity entity : sub.getLevel().getAllEntities()) {
            if (entity instanceof LivingEntity mob && drives(mob, trainId)) {
                if (legacy == null && mob.getPersistentData().contains("StationSchedule")) {
                    legacy = mob.getPersistentData().getCompound("StationSchedule");
                }

                stripHat(mob);
                PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), false), new CustomPacketPayload[0]);
            }
        }

        for (BlazeBurnerBlockEntity burner : SubLevelBlocks.findAll(sub, BlazeBurnerBlockEntity.class)) {
            if (burner.hat) {
                setBlazeHat(sub, burner, burner.getBlockPos(), false);
            }
        }

        CompoundTag tag = takeConsistSchedule(level, trainId);
        handBackSchedule(level, at, tag != null ? tag : legacy, sender);
    }

    private static void handBackSchedule(ServerLevel level, BlockPos at, @Nullable CompoundTag tag, @Nullable ServerPlayer sender) {
        if (tag != null) {
            if (sender != null && sender.getMainHandItem().isEmpty()) {
                ItemStack returned = AllItems.SCHEDULE.asStack();
                returned.set(AllDataComponents.TRAIN_SCHEDULE, tag);
                sender.getInventory().placeItemBackInInventory(returned);
                AllSoundEvents.playItemPickup(sender);
            } else {
                dropSchedule(level, at, tag);
            }
        }
    }

    @SubscribeEvent
    public static void onStampStation(RightClickBlock event) {
        Player player = event.getEntity();
        if (player != null && !player.isSpectator() && event.getHand() == InteractionHand.MAIN_HAND) {
            if (player.isShiftKeyDown()) {
                ItemStack held = event.getItemStack();
                if (AllItems.SCHEDULE.isIn(held)) {
                    Level level = event.getLevel();
                    BlockPos pos = event.getPos();
                    if (level.getBlockEntity(pos) instanceof StationBlockEntity station) {
                        if (!player.getCooldowns().isOnCooldown((Item)AllItems.SCHEDULE.get())) {
                            if (!level.isClientSide) {
                                addStop(player, level, pos, held, station);
                            }

                            player.getCooldowns().addCooldown((Item)AllItems.SCHEDULE.get(), 5);
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    private static void addStop(Player player, Level level, BlockPos pos, ItemStack held, StationBlockEntity station) {
        GlobalStation global = station.getStation();
        if (global != null && !global.name.isBlank()) {
            Schedule schedule = readSchedule(player, held);
            if (schedule == null) {
                schedule = new Schedule();
            }

            CompoundTag data = new CompoundTag();
            data.putString("Text", global.name);
            DestinationInstruction destination = new DestinationInstruction();
            destination.setData(player.registryAccess(), data);
            List<ScheduleWaitCondition> initialConditions = new ArrayList<>();
            initialConditions.add(new ScheduledDelay());
            List<List<ScheduleWaitCondition>> conditions = new ArrayList<>();
            conditions.add(initialConditions);
            schedule.entries.add(new ScheduleEntry(destination, conditions));
            held.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(player.registryAccess()));
            AllSoundEvents.CONFIRM.playOnServer(level, pos, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("loconautics.schedule.stop_added", new Object[]{Component.literal(global.name)}), true);
        } else {
            deny(player, level, pos, "station_unnamed");
        }
    }

    @SubscribeEvent
    public static void onStartTracking(StartTracking event) {
        if (event.getTarget() instanceof LivingEntity mob) {
            if (mob.getPersistentData().contains("TrainHat")) {
                if (event.getEntity() instanceof ServerPlayer sp) {
                    PacketDistributor.sendToPlayer(sp, new ConductorHatPacket(mob.getId(), true), new CustomPacketPayload[0]);
                }
            }
        }
    }

    private static void handOverSchedule(Player player, LivingEntity mob, ItemStack held, boolean isConductor) {
        Level level = player.level();
        BlockPos at = mob.blockPosition();
        ServerSubLevel sub = subLevelOf(mob.getVehicle());
        SableTrain train = trainOf(sub);
        boolean shared = train != null && SableScheduleRuntime.consistKey(train) != null;
        if (!isConductor || train != null && !shared) {
            AnalogControllerBlockEntity controller = sub == null ? null : ScheduleReadiness.controllerAt(sub, BlockPos.containing(mob.getVehicle().position()));
            if (controller == null) {
                deny(player, level, at, "seat_not_facing");
            } else if (train == null) {
                deny(player, level, at, "no_train");
            } else if (shared) {
                joinAsMob(player, mob, at, sub, controller, train);
            } else {
                Schedule schedule = validateSchedule(player, level, at, sub, controller, held);
                if (schedule != null) {
                    CompoundTag written = schedule.write(player.registryAccess());
                    mob.getPersistentData().putBoolean("TrainHat", true);
                    mob.getPersistentData().putUUID("AdvTrain", train.id());
                    ScheduleProgressStore.get(level.getServer()).putSchedule(train.id(), written);
                    SableScheduleRuntime.assign(train.id(), schedule);
                    PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), true), new CustomPacketPayload[0]);
                    AllSoundEvents.CONFIRM.playOnServer(level, at, 1.0F, 1.0F);
                    TrainAdvancements.conductor(player);
                    player.displayClientMessage(Component.translatable("loconautics.schedule.now_conductor", new Object[]{mob.getDisplayName()}), true);
                    held.shrink(1);
                }
            }
        } else {
            deny(player, level, at, "already_conductor");
        }
    }

    private static void joinAsMob(Player player, LivingEntity mob, BlockPos at, ServerSubLevel sub, AnalogControllerBlockEntity controller, SableTrain train) {
        Level level = player.level();
        if (hardwareOk(player, level, at, sub, controller)) {
            mob.getPersistentData().putBoolean("TrainHat", true);
            mob.getPersistentData().putUUID("AdvTrain", train.id());
            PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), true), new CustomPacketPayload[0]);
            AllSoundEvents.CONFIRM.playOnServer(level, at, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("loconautics.schedule.co_driver", new Object[]{mob.getDisplayName()}), true);
        }
    }

    private static boolean hardwareOk(Player player, Level level, BlockPos at, ServerSubLevel sub, AnalogControllerBlockEntity controller) {
        LocomotiveHardware.Result hardware = LocomotiveHardware.validate(sub, controller);
        if (!hardware.ok()) {
            deny(player, level, at, hardware.messageKey());
        }

        return hardware.ok();
    }

    private static Schedule validateSchedule(
        Player player, Level level, BlockPos at, ServerSubLevel sub, AnalogControllerBlockEntity controller, ItemStack held
    ) {
        if (!hardwareOk(player, level, at, sub, controller)) {
            return null;
        }

        Schedule schedule = readSchedule(player, held);
        if (schedule != null && !schedule.entries.isEmpty()) {
            return schedule;
        }

        deny(player, level, at, "no_stops");
        return null;
    }

    public static Schedule readSchedule(Player player, ItemStack stack) {
        return !stack.has(AllDataComponents.TRAIN_SCHEDULE)
            ? null
            : Schedule.fromTag(player.registryAccess(), (CompoundTag)stack.get(AllDataComponents.TRAIN_SCHEDULE));
    }

    private static void deny(Player player, Level level, BlockPos at, String messageKey) {
        AllSoundEvents.DENY.playOnServer(level, at, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("loconautics.schedule." + messageKey), true);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (SableTrain train : SableTrainRegistry.all()) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
                UUID id = train.car().subLevelId();
                if (container != null && id != null && container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                    for (BlazeConductorHatPacket packet : blazeHats(sub)) {
                        PacketDistributor.sendToPlayer(player, packet, new CustomPacketPayload[0]);
                    }
                }
            }
        }
    }

    public static void broadcastBlazeHats(ServerSubLevel sub) {
        for (BlazeConductorHatPacket packet : blazeHats(sub)) {
            PacketDistributor.sendToAllPlayers(packet, new CustomPacketPayload[0]);
        }
    }

    private static List<BlazeConductorHatPacket> blazeHats(ServerSubLevel sub) {
        List<BlazeConductorHatPacket> packets = new ArrayList<>();

        for (BlazeBurnerBlockEntity burner : SubLevelBlocks.findAll(sub, BlazeBurnerBlockEntity.class)) {
            if (burner.hat) {
                packets.add(new BlazeConductorHatPacket(sub.getUniqueId(), burner.getBlockPos(), true));
            }
        }

        return packets;
    }

    private static void setBlazeHat(@Nullable ServerSubLevel sub, BlazeBurnerBlockEntity burner, BlockPos pos, boolean hat) {
        burner.hat = hat;
        burner.notifyUpdate();
        if (sub != null) {
            PacketDistributor.sendToAllPlayers(new BlazeConductorHatPacket(sub.getUniqueId(), pos, hat), new CustomPacketPayload[0]);
        }
    }

    private static ServerSubLevel subLevelOf(Entity seat) {
        return seat == null ? null : subLevelAt(seat.level(), BlockPos.containing(seat.position()));
    }

    private static ServerSubLevel subLevelAt(Level level, BlockPos at) {
        for (SableTrain train : SableTrainRegistry.all()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            if (container != null) {
                UUID id = train.car().subLevelId();
                if (id != null
                    && container.getSubLevel(id) instanceof ServerSubLevel sub
                    && !sub.isRemoved()
                    && sub.getLevel() == level
                    && sub.getPlot().getBoundingBox().contains(at.getX(), at.getY(), at.getZ())) {
                    return sub;
                }
            }
        }

        return null;
    }

    private static SableTrain trainOf(ServerSubLevel sub) {
        return sub == null ? null : SableTrainRegistry.bySubLevel(sub.getUniqueId());
    }

    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (event.isDismounting()) {
            if (!event.getLevel().isClientSide) {
                if (event.getEntityMounting() instanceof LivingEntity mob) {
                    if (mob.getPersistentData().contains("TrainHat")) {
                        relieveAndDrop(mob);
                        PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), false), new CustomPacketPayload[0]);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onConductorDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!mob.level().isClientSide && mob.getPersistentData().contains("TrainHat")) {
            relieveAndDrop(mob);
            PacketDistributor.sendToPlayersTrackingEntity(mob, new ConductorHatPacket(mob.getId(), false), new CustomPacketPayload[0]);
        }
    }

    @SubscribeEvent
    public static void onBreakBlazeDriver(BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            BlockPos pos = event.getPos();
            if (level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner && burner.hat) {
                SableTrain train = trainOf(subLevelAt(level, pos));
                if (train != null && level.getServer() != null) {
                    setBlazeHat(subLevelAt(level, pos), burner, pos, false);
                    if (!stillDriven(train)) {
                        dropSchedule(level, pos, takeConsistSchedule(level, train.id()));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onConductorLoaded(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity mob) {
            CompoundTag data = mob.getPersistentData();
            if (data.contains("TrainHat") && data.hasUUID("AdvTrain") && data.contains("StationSchedule")) {
                MinecraftServer server = event.getLevel().getServer();
                if (server != null) {
                    UUID trainId = data.getUUID("AdvTrain");
                    ScheduleProgressStore store = ScheduleProgressStore.get(server);
                    if (!store.hasSchedule(trainId)) {
                        store.putSchedule(trainId, data.getCompound("StationSchedule"));
                    }

                    data.remove("StationSchedule");
                }
            }
        }
    }

    private static void relieveAndDrop(LivingEntity mob) {
        CompoundTag data = mob.getPersistentData();
        SableTrain car = data.hasUUID("AdvTrain") ? SableTrainRegistry.get(data.getUUID("AdvTrain")) : null;
        data.remove("TrainHat");
        if (car != null && stillDriven(car)) {
            LoconauticsConstants.LOGGER
                .debug("[schedshare] conductor on {} left, the consist schedule stays with the other drivers", LogFormat.shortId(car.id()));
            stripHat(mob);
        } else {
            dropSchedule(mob.level(), mob.blockPosition(), takeFrom(mob));
        }
    }

    @Nullable
    private static CompoundTag takeFrom(LivingEntity mob) {
        CompoundTag data = mob.getPersistentData();
        CompoundTag tag = data.hasUUID("AdvTrain") ? takeConsistSchedule(mob.level(), data.getUUID("AdvTrain")) : null;
        if (tag == null && data.contains("StationSchedule")) {
            tag = data.getCompound("StationSchedule");
        }

        stripHat(mob);
        return tag;
    }

    @Nullable
    private static CompoundTag takeConsistSchedule(Level level, UUID carId) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }

        UUID key = SableScheduleRuntime.consistKey(carId);
        ScheduleProgressStore store = ScheduleProgressStore.get(server);
        CompoundTag tag = store.getSchedule(key);
        SableScheduleRuntime runtime = SableScheduleRuntime.get(key);
        if (tag == null && runtime != null && runtime.schedule != null) {
            tag = runtime.schedule.write(server.registryAccess());
        }

        tag = withProgress(key, tag);
        SableScheduleRuntime.clear(key);
        store.remove(key);
        return tag;
    }

    private static boolean stillDriven(SableTrain car) {
        for (SableTrain mate : SableScheduleRuntime.consistCars(car)) {
            if (ScheduleReadiness.drivenController(SableTrainDriver.axleSub(mate), mate.id()) != null) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static CompoundTag withProgress(@Nullable UUID trainId, @Nullable CompoundTag tag) {
        if (tag != null && trainId != null) {
            SableScheduleRuntime runtime = SableScheduleRuntime.get(trainId);
            if (runtime == null) {
                return tag;
            }

            CompoundTag stamped = tag.copy();
            stamped.putInt("Progress", runtime.currentEntry);
            return stamped;
        } else {
            return tag;
        }
    }

    static void dropSchedule(Level level, BlockPos pos, @Nullable CompoundTag tag) {
        if (tag != null) {
            ItemStack stack = AllItems.SCHEDULE.asStack();
            stack.set(AllDataComponents.TRAIN_SCHEDULE, tag);
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
    }

    private static void stripHat(LivingEntity mob) {
        CompoundTag data = mob.getPersistentData();
        data.remove("TrainHat");
        data.remove("StationSchedule");
        data.remove("AdvTrain");
    }

    private static void consume(EntityInteractSpecific event, Player player) {
        player.getCooldowns().addCooldown((Item)AllItems.SCHEDULE.get(), 5);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
