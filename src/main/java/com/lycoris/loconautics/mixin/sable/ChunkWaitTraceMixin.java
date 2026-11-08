package com.lycoris.loconautics.mixin.sable;

import com.google.common.collect.Iterables;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.LogFormat;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PhysicsChunkTicketManager.class, remap = false)
public abstract class ChunkWaitTraceMixin {
    @Unique
    private static final double LOG_ABOVE_MS = 1.0;
    @Unique
    private static final int SUMMARY_EVERY_TICKS = 100;
    @Unique
    private static UUID loconautics$inhabiting;

    @Inject(method = "inhabitChunk", at = @At("HEAD"))
    private void loconautics$noteInhabiter(
        ServerLevel level, DistanceManager distanceManager, UUID subLevelId, long gameTime, long chunkLong, int x, int z, CallbackInfo ci
    ) {
        loconautics$inhabiting = subLevelId;
    }

    @WrapOperation(
        method = "inhabitChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"
        )
    )
    private ChunkAccess loconautics$timeInhabit(ServerLevel level, int x, int z, ChunkStatus status, boolean load, Operation<ChunkAccess> original) {
        boolean cold = level.getChunkSource().getChunkNow(x, z) == null;
        long start = System.nanoTime();
        ChunkAccess chunk = (ChunkAccess)original.call(new Object[]{level, x, z, status, load});
        loconautics$log("inhabit", level, loconautics$inhabiting, x, z, cold, start);
        return chunk;
    }

    @WrapOperation(
        method = "addTicket",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;")
    )
    private LevelChunk loconautics$timeSection(Level level, int x, int z, Operation<LevelChunk> original) {
        boolean cold = level instanceof ServerLevel server && server.getChunkSource().getChunkNow(x, z) == null;
        long start = System.nanoTime();
        LevelChunk chunk = (LevelChunk)original.call(new Object[]{level, x, z});
        if (level instanceof ServerLevel server) {
            loconautics$log("section", server, null, x, z, cold, start);
        }

        return chunk;
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void loconautics$summary(
        ServerLevel level, ServerSubLevelContainer container, SubLevelPhysicsSystem system, PhysicsPipeline pipeline, double timeStep, CallbackInfo ci
    ) {
        if (level.getGameTime() % 100L == 0L) {
            int tickets = 0;
            StringBuilder holders = new StringBuilder();

            for (Entry<ServerSubLevel, Set<SubLevelLoadingTicket<?>>> entry : container.collectForceLoadTickets().entrySet()) {
                tickets += entry.getValue().size();
                holders.append(' ')
                    .append(LogFormat.shortId(entry.getKey()))
                    .append(entry.getValue().stream().map(SubLevelLoadingTicket::toCompactString).toList());
            }

            LoconauticsConstants.LOGGER
                .info(
                    "[chunkwait] summary dim {} subLevels {} arbitraryObjects {} forceTickets {} forceLoaded {} holders{}",
                    new Object[]{
                        level.dimension().location(),
                        container.getAllSubLevels().size(),
                        Iterables.size(system.getArbitraryObjects()),
                        tickets,
                        container.collectForceLoadedSubLevels().size(),
                        holders
                    }
                );
        }
    }

    @Unique
    private static void loconautics$log(String kind, ServerLevel level, UUID subLevelId, int x, int z, boolean cold, long start) {
        double ms = (System.nanoTime() - start) / 1000000.0;
        if (cold || !(ms < 1.0)) {
            String tickets = "none";
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null && subLevelId != null && container.getSubLevel(subLevelId) instanceof ServerSubLevel sub) {
                Set<SubLevelLoadingTicket<?>> held = (Set<SubLevelLoadingTicket<?>>)container.collectForceLoadTickets().get(sub);
                tickets = held == null ? "none" : held.stream().map(SubLevelLoadingTicket::toCompactString).toList().toString();
            }

            LoconauticsConstants.LOGGER
                .info(
                    "[chunkwait] {} chunk {},{} cold {} took {} ms sub {} tickets {} tick {}",
                    new Object[]{kind, x, z, cold, String.format(Locale.ROOT, "%.2f", ms), LogFormat.shortId(subLevelId), tickets, level.getGameTime()}
                );
        }
    }
}
