package com.lycoris.loconautics.content.schedule;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;

public abstract class ScheduleDataEntry implements IScheduleInput {
    protected CompoundTag data = new CompoundTag();

    @Override
    public CompoundTag getData() {
        return this.data;
    }

    @Override
    public void setData(Provider registries, CompoundTag data) {
        this.data = data;
        this.readAdditional(registries, data);
    }

    public final CompoundTag write(Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag dataCopy = this.data.copy();
        this.writeAdditional(registries, dataCopy);
        tag.putString("Id", this.getId().toString());
        tag.put("Data", dataCopy);
        return tag;
    }

    protected void writeAdditional(Provider registries, CompoundTag tag) {
    }

    protected void readAdditional(Provider registries, CompoundTag tag) {
    }

    protected <T> T enumData(String key, Class<T> enumClass) {
        T[] enumConstants = enumClass.getEnumConstants();
        return enumConstants[this.data.getInt(key) % enumConstants.length];
    }

    protected String textData(String key) {
        return this.data.getString(key);
    }

    protected int intData(String key) {
        return this.data.getInt(key);
    }
}
