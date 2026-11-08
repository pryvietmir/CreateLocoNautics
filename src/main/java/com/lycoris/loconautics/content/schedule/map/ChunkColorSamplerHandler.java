package com.lycoris.loconautics.content.schedule.map;

import com.simibubi.create.content.trains.track.ITrackBlock;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;

public class ChunkColorSamplerHandler {
    private static final TagKey<Block> CREATE_TRACKS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", "tracks"));
    private static ChunkColorSamplerHandler instance;
    private final Queue<ChunkColorSamplerHandler.PendingSample> pending = new ConcurrentLinkedQueue<>();

    public static ChunkColorSamplerHandler get() {
        return instance;
    }

    public ChunkColorSamplerHandler() {
        instance = this;
    }

    public void forceResample(ServerLevel level, ChunkPos pos) {
        this.pending.add(new ChunkColorSamplerHandler.PendingSample(level, pos, true));
    }

    @SubscribeEvent
    public void onChunkLoad(Load event) {
        if (!event.getLevel().isClientSide()) {
            if (event.getLevel() instanceof ServerLevel level) {
                ChunkPos var4 = event.getChunk().getPos();
                if (this.hasNearbyPlayer(level, var4)) {
                    this.pending.add(new ChunkColorSamplerHandler.PendingSample(level, var4, false));
                }
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BlockState state = event.getState();
            if (state.getBlock() instanceof ITrackBlock || state.is(CREATE_TRACKS)) {
                this.forceResample(level, new ChunkPos(event.getPos()));
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(Post event) {
        ChunkColorSamplerHandler.PendingSample sample;
        for (int processed = 0; processed < 16 && (sample = this.pending.poll()) != null; processed++) {
            this.processChunk(sample.level, sample.pos, sample.force);
        }
    }

    private void processChunk(ServerLevel level, ChunkPos pos, boolean force) {
        if (force || MapColorStorage.get(level).read(pos.x, pos.z) == null) {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                byte[] colors = ChunkMapColorSampler.sample(level, pos);
                MapColorStorage.get(level).write(pos.x, pos.z, colors);
            }
        }
    }

    private boolean hasNearbyPlayer(ServerLevel level, ChunkPos pos) {
        int centerX = pos.getMiddleBlockX();
        int centerZ = pos.getMiddleBlockZ();
        return level.getServer().getPlayerList().getPlayers().stream().filter(p -> p.level() == level).anyMatch(p -> {
            double dx = p.getX() - centerX;
            double dz = p.getZ() - centerZ;
            return dx * dx + dz * dz < 16384.0;
        });
    }

    private record PendingSample(ServerLevel level, ChunkPos pos, boolean force) {
    }
}
