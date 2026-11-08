package com.lycoris.loconautics.content.trainlookup.history;

import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record HistoryEntry(
    long timestamp,
    HistoryEvent event,
    UUID trainUUID,
    @Nullable String trainName,
    UUID actorUUID,
    String actorName,
    ResourceKey<Level> dimension,
    Vec3 position,
    int carriageIndex,
    @Nullable String carriageLabel,
    @Nullable String notes
) {
    public static final int NO_CARRIAGE = -1;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Time", this.timestamp);
        tag.putString("Event", this.event.name());
        tag.putUUID("Train", this.trainUUID);
        tag.putUUID("Actor", this.actorUUID);
        tag.putString("ActorName", this.actorName);
        tag.putString("Dimension", this.dimension.location().toString());
        tag.putDouble("X", this.position.x);
        tag.putDouble("Y", this.position.y);
        tag.putDouble("Z", this.position.z);
        tag.putInt("Carriage", this.carriageIndex);
        if (this.trainName != null) {
            tag.putString("TrainName", this.trainName);
        }

        if (this.carriageLabel != null) {
            tag.putString("CarriageLabel", this.carriageLabel);
        }

        if (this.notes != null) {
            tag.putString("Notes", this.notes);
        }

        return tag;
    }

    @Nullable
    public static HistoryEntry load(CompoundTag tag) {
        HistoryEvent event = HistoryEvent.byName(tag.getString("Event"));
        if (event != null && tag.hasUUID("Train") && tag.hasUUID("Actor")) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dimension"));
            return dim == null
                ? null
                : new HistoryEntry(
                    tag.getLong("Time"),
                    event,
                    tag.getUUID("Train"),
                    tag.contains("TrainName") ? tag.getString("TrainName") : null,
                    tag.getUUID("Actor"),
                    tag.getString("ActorName"),
                    ResourceKey.create(Registries.DIMENSION, dim),
                    new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                    tag.getInt("Carriage"),
                    tag.contains("CarriageLabel") ? tag.getString("CarriageLabel") : null,
                    tag.contains("Notes") ? tag.getString("Notes") : null
                );
        } else {
            return null;
        }
    }
}
