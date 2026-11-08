package com.lycoris.loconautics.content.boiler;

public interface BoilerWaterSource {
    int getWaterAmount();

    int getWaterCapacity();

    int drawWater(int var1);

    void returnWater(int var1);

    default void setWaterCapacity(int millibuckets) {
    }
}
