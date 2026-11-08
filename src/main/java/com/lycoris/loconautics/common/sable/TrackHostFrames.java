package com.lycoris.loconautics.common.sable;

import com.mojang.serialization.DataResult;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class TrackHostFrames {
    private static final Map<UUID, TrackHostFrames.Frame> FRAMES = new ConcurrentHashMap<>();
    private static final List<Consumer<UUID>> FORGET_LISTENERS = new CopyOnWriteArrayList<>();

    private TrackHostFrames() {
    }

    @Nullable
    public static TrackHostFrames.Frame read(CompoundTag tag) {
        if (tag.hasUUID("Host") && tag.contains("Pose")) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            Pose3d pose = dimension == null ? null : (Pose3d)Pose3d.CODEC.parse(NbtOps.INSTANCE, tag.get("Pose")).result().orElse(null);
            if (pose == null) {
                return null;
            }

            TrackHostFrames.Frame frame = new TrackHostFrames.Frame(tag.getUUID("Host"), ResourceKey.create(Registries.DIMENSION, dimension));
            frame.minChunkX = tag.getInt("MinX");
            frame.minChunkZ = tag.getInt("MinZ");
            frame.maxChunkX = tag.getInt("MaxX");
            frame.maxChunkZ = tag.getInt("MaxZ");
            frame.rebase(
                pose,
                tag.getDouble("BaseAngle"),
                new Vec3(tag.getDouble("AxisX"), tag.getDouble("AxisY"), tag.getDouble("AxisZ")),
                new Vec3(tag.getDouble("NormalX"), tag.getDouble("NormalY"), tag.getDouble("NormalZ"))
            );
            frame.setAngle(tag.getDouble("Angle"));
            return frame;
        } else {
            return null;
        }
    }

    public static void restore(TrackHostFrames.Frame frame) {
        FRAMES.putIfAbsent(frame.host(), frame);
    }

    public static void record(UUID host, ResourceKey<Level> dimension, ServerSubLevel sub, double angle, Vec3 axis, Vec3 normal) {
        TrackHostFrames.Frame frame = FRAMES.computeIfAbsent(host, id -> new TrackHostFrames.Frame(id, dimension));
        fitBounds(frame, sub);
        frame.rebase(sub.logicalPose(), angle, axis, normal);
        frame.loaded = true;
    }

    public static void onUnloaded(ServerSubLevel sub, ResourceKey<Level> dimension) {
        UUID id = sub.getUniqueId();
        TrackHostFrames.Frame known = FRAMES.get(id);
        if (known != null) {
            known.loaded = false;
            if (known.axis.lengthSqr() == 0.0) {
                known.rebase(sub.logicalPose(), 0.0, Vec3.ZERO, known.normal);
            }
        } else {
            LevelPlot plot = sub.getPlot();
            if (plot != null && hostsTrack(dimension, plot.getChunkMin(), plot.getChunkMax())) {
                TrackHostFrames.Frame frame = new TrackHostFrames.Frame(id, dimension);
                fitBounds(frame, sub);
                frame.rebase(sub.logicalPose(), 0.0, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0));
                FRAMES.put(id, frame);
            }
        }
    }

    public static void onLoaded(UUID host) {
        TrackHostFrames.Frame frame = FRAMES.get(host);
        if (frame != null) {
            frame.loaded = true;
        }
    }

    private static void fitBounds(TrackHostFrames.Frame frame, ServerSubLevel sub) {
        LevelPlot plot = sub.getPlot();
        ChunkPos min = plot.getChunkMin();
        ChunkPos max = plot.getChunkMax();
        frame.minChunkX = min.x;
        frame.minChunkZ = min.z;
        frame.maxChunkX = max.x;
        frame.maxChunkZ = max.z;
    }

    private static boolean hostsTrack(ResourceKey<Level> dimension, ChunkPos min, ChunkPos max) {
        for (TrackGraph graph : networks()) {
            for (TrackNodeLocation location : graph.getNodes()) {
                if (location.dimension == dimension) {
                    int chunkX = Mth.floor(location.getX() / 2.0) >> 4;
                    int chunkZ = Mth.floor(location.getZ() / 2.0) >> 4;
                    if (chunkX >= min.x && chunkX <= max.x && chunkZ >= min.z && chunkZ <= max.z) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static List<TrackGraph> networks() {
        return Create.RAILWAYS == null ? List.of() : new ArrayList<>(Create.RAILWAYS.trackNetworks.values());
    }

    public static List<UUID> orphans() {
        List<UUID> gone = new ArrayList<>();
        List<TrackGraph> graphs = networks();
        if (graphs.isEmpty()) {
            return gone;
        }

        for (TrackHostFrames.Frame frame : FRAMES.values()) {
            if (!frame.loaded && !hostsTrack(frame.dimension, new ChunkPos(frame.minChunkX, frame.minChunkZ), new ChunkPos(frame.maxChunkX, frame.maxChunkZ))) {
                gone.add(frame.host());
            }
        }

        return gone;
    }

    @Nullable
    public static TrackHostFrames.Frame frameOf(@Nullable UUID host) {
        return host == null ? null : FRAMES.get(host);
    }

    public static void setAngle(@Nullable UUID host, double degrees) {
        TrackHostFrames.Frame frame = frameOf(host);
        if (frame != null) {
            frame.setAngle(degrees);
        }
    }

    public static void onForget(Consumer<UUID> listener) {
        FORGET_LISTENERS.add(listener);
    }

    public static void forget(@Nullable UUID host) {
        if (host != null) {
            FRAMES.remove(host);
            FORGET_LISTENERS.forEach(listener -> listener.accept(host));
        }
    }

    public static void clear() {
        FRAMES.clear();
    }

    @Nullable
    public static TrackHostFrames.Frame frameAt(@Nullable TrackNodeLocation location) {
        if (location != null && !FRAMES.isEmpty() && location.dimension != null) {
            int chunkX = Mth.floor(location.getX() / 2.0) >> 4;
            int chunkZ = Mth.floor(location.getZ() / 2.0) >> 4;

            for (TrackHostFrames.Frame frame : FRAMES.values()) {
                if (frame.contains(location.dimension, chunkX, chunkZ)) {
                    return frame;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    @Nullable
    public static UUID hostId(@Nullable TrackNodeLocation location) {
        TrackHostFrames.Frame frame = frameAt(location);
        return frame == null ? null : frame.host();
    }

    @Nullable
    public static Pose3dc poseAt(@Nullable TrackNodeLocation location) {
        TrackHostFrames.Frame frame = frameAt(location);
        return frame == null ? null : frame.pose();
    }

    public static Collection<TrackHostFrames.Frame> all() {
        return FRAMES.values();
    }

    public static List<TrackHostFrames.Frame> unloaded() {
        List<TrackHostFrames.Frame> out = new ArrayList<>();

        for (TrackHostFrames.Frame frame : FRAMES.values()) {
            if (!frame.loaded) {
                out.add(frame);
            }
        }

        return out;
    }

    public static final class Frame {
        private final UUID host;
        private final ResourceKey<Level> dimension;
        private int minChunkX;
        private int minChunkZ;
        private int maxChunkX;
        private int maxChunkZ;
        private final Pose3d basePose = new Pose3d();
        private double baseAngle;
        private double angle;
        private Vec3 axis = Vec3.ZERO;
        private Vec3 normal = new Vec3(0.0, 1.0, 0.0);
        private Pose3d cached;
        private double cachedAngle = Double.NaN;
        private volatile boolean loaded;

        Frame(UUID host, ResourceKey<Level> dimension) {
            this.host = host;
            this.dimension = dimension;
        }

        public UUID host() {
            return this.host;
        }

        public ResourceKey<Level> dimension() {
            return this.dimension;
        }

        public Vec3 axis() {
            return this.axis;
        }

        public Vec3 normal() {
            return this.normal;
        }

        public int minChunkX() {
            return this.minChunkX;
        }

        public int minChunkZ() {
            return this.minChunkZ;
        }

        public int maxChunkX() {
            return this.maxChunkX;
        }

        public int maxChunkZ() {
            return this.maxChunkZ;
        }

        public boolean loaded() {
            return this.loaded;
        }

        public synchronized double angle() {
            return this.angle;
        }

        public synchronized void setAngle(double degrees) {
            this.angle = degrees;
        }

        boolean contains(ResourceKey<Level> dim, int chunkX, int chunkZ) {
            return this.dimension.equals(dim) && chunkX >= this.minChunkX && chunkX <= this.maxChunkX && chunkZ >= this.minChunkZ && chunkZ <= this.maxChunkZ;
        }

        public synchronized Pose3dc pose() {
            if (this.cached != null && this.cachedAngle == this.angle) {
                return this.cached;
            }

            Pose3d out = new Pose3d(this.basePose);
            double delta = Math.toRadians(this.angle - this.baseAngle);
            if (delta != 0.0) {
                Quaterniond turn = new Quaterniond().rotationAxis(delta, this.normal.x, this.normal.y, this.normal.z);
                Vector3d offset = new Vector3d(out.position()).sub(this.axis.x, this.axis.y, this.axis.z);
                turn.transform(offset);
                out.position().set(this.axis.x + offset.x, this.axis.y + offset.y, this.axis.z + offset.z);
                turn.mul(out.orientation(), out.orientation());
                out.orientation().normalize();
            }

            this.cached = out;
            this.cachedAngle = this.angle;
            return out;
        }

        synchronized void rebase(Pose3dc pose, double degrees, Vec3 axisWorld, Vec3 normalWorld) {
            this.basePose.set(pose);
            this.baseAngle = degrees;
            this.angle = degrees;
            this.axis = axisWorld;
            this.normal = normalWorld;
            this.cached = null;
            this.cachedAngle = Double.NaN;
        }

        synchronized CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Host", this.host);
            tag.putString("Dimension", this.dimension.location().toString());
            tag.putInt("MinX", this.minChunkX);
            tag.putInt("MinZ", this.minChunkZ);
            tag.putInt("MaxX", this.maxChunkX);
            tag.putInt("MaxZ", this.maxChunkZ);
            tag.putDouble("BaseAngle", this.baseAngle);
            tag.putDouble("Angle", this.angle);
            tag.putDouble("AxisX", this.axis.x);
            tag.putDouble("AxisY", this.axis.y);
            tag.putDouble("AxisZ", this.axis.z);
            tag.putDouble("NormalX", this.normal.x);
            tag.putDouble("NormalY", this.normal.y);
            tag.putDouble("NormalZ", this.normal.z);
            DataResult<Tag> pose = Pose3d.CODEC.encodeStart(NbtOps.INSTANCE, this.basePose);
            pose.result().ifPresent(value -> tag.put("Pose", value));
            return tag;
        }
    }
}
