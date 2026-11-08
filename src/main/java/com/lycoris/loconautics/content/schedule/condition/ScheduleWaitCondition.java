package com.lycoris.loconautics.content.schedule.condition;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.content.schedule.ScheduleDataEntry;
import com.lycoris.loconautics.trainphysics.SableTrain;
import java.util.function.Supplier;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public abstract class ScheduleWaitCondition extends ScheduleDataEntry {
    public static final StreamCodec<RegistryFriendlyByteBuf, ScheduleWaitCondition> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ScheduleWaitCondition>() {
        public ScheduleWaitCondition decode(RegistryFriendlyByteBuf buffer) {
            return ScheduleWaitCondition.fromTag(buffer.registryAccess(), buffer.readNbt());
        }

        public void encode(RegistryFriendlyByteBuf buffer, ScheduleWaitCondition value) {
            buffer.writeNbt(value.write(buffer.registryAccess()));
        }
    };

    public abstract boolean tickCompletion(Level var1, SableTrain var2, CompoundTag var3);

    public boolean needsBody() {
        return false;
    }

    public boolean needsStationUnloaded() {
        return false;
    }

    public int carriageIndex() {
        return this.data.contains("Carriage") ? this.data.getInt("Carriage") : -1;
    }

    protected void requestStatusToUpdate(CompoundTag context) {
        context.putInt("StatusVersion", context.getInt("StatusVersion") + 1);
    }

    public static ScheduleWaitCondition fromTag(Provider registries, CompoundTag tag) {
        ResourceLocation location = ResourceLocation.parse(tag.getString("Id"));
        Supplier<? extends ScheduleWaitCondition> supplier = null;

        for (Pair<ResourceLocation, Supplier<? extends ScheduleWaitCondition>> pair : Schedule.CONDITION_TYPES) {
            if (((ResourceLocation)pair.getFirst()).equals(location)) {
                supplier = (Supplier<? extends ScheduleWaitCondition>)pair.getSecond();
            }
        }

        if (supplier == null) {
            LoconauticsConstants.LOGGER.warn("Could not parse waiting condition type: " + location);
            return null;
        } else {
            ScheduleWaitCondition condition = supplier.get();
            CompoundTag data = tag.getCompound("Data");
            condition.readAdditional(registries, data);
            condition.data = data;
            return condition;
        }
    }

    public abstract MutableComponent getWaitingStatus(Level var1, SableTrain var2, CompoundTag var3);
}
